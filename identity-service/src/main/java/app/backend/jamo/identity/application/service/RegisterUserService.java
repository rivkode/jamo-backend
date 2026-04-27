package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.RegisterUserCommand;
import app.backend.jamo.identity.application.dto.RegisterUserResult;
import app.backend.jamo.identity.domain.exception.EmailAlreadyRegisteredException;
import app.backend.jamo.identity.domain.exception.EmailNotValidatedException;
import app.backend.jamo.identity.domain.model.user.DisplayName;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.HashedPassword;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.repository.EmailValidatedFlag;
import app.backend.jamo.identity.domain.repository.PasswordEncoder;
import app.backend.jamo.identity.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * LOCAL 회원가입 use case (PRD user/createUser.md §9).
 *
 * <p>흐름:
 * <ol>
 *   <li>VO 검증 — {@link Email}, {@link DisplayName} 의 컴팩트 컨스트럭터.</li>
 *   <li><b>트랜잭션 외부</b>: {@link EmailValidatedFlag#consume(Email)} (Redis GETDEL 단발 호출).
 *       PRD §9 — "소비형, 같은 이메일 재가입 시도 방지". flag 가 없으면 즉시 {@link EmailNotValidatedException}.</li>
 *   <li><b>트랜잭션 외부</b>: {@link PasswordEncoder#encode(String)} — BCrypt cost=12 는 ~300ms 의 CPU
 *       작업이라 트랜잭션 안에서 실행하면 DB 커넥션이 그동안 점유돼 connection pool 고갈 위험
 *       (PR6-b security review H2). {@link TransactionTemplate} 으로 트랜잭션 경계를 좁힘.</li>
 *   <li><b>트랜잭션 내부</b>: {@link UserRepository#existsLocalAccountByEmail(Email)} 중복 체크 →
 *       {@link User#registerLocal} → {@link UserRepository#save}. 빠른 IO 만 트랜잭션 안에 둠.</li>
 * </ol>
 *
 * <p>flag 가 가장 먼저 소비되는 trade-off — 사용자가 (예: VO 검증은 통과했으나) DB 단계에서
 * 중복으로 실패하면 flag 는 이미 사라져 검증 코드 재발급 필요. 이는 재가입 시도 차단을 위한
 * 의도된 설계 (PRD §9). 선의의 사용자 UX 디그라데이션은 client validation + presentation
 * Bean Validation (PR6-c) 에서 1차 차단으로 완화 가능.
 */
@Service
public class RegisterUserService {

    private final EmailValidatedFlag emailValidatedFlag;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public RegisterUserService(EmailValidatedFlag emailValidatedFlag,
                               UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               TransactionTemplate transactionTemplate,
                               Clock clock) {
        this.emailValidatedFlag = Objects.requireNonNull(emailValidatedFlag, "emailValidatedFlag");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RegisterUserResult register(RegisterUserCommand command) {
        Objects.requireNonNull(command, "command");

        Email email = new Email(command.email());
        DisplayName displayName = new DisplayName(command.displayName());

        if (!emailValidatedFlag.consume(email)) {
            throw new EmailNotValidatedException("email validation flag missing or expired");
        }

        HashedPassword hashedPassword = passwordEncoder.encode(command.rawPassword());

        return transactionTemplate.execute(status -> {
            if (userRepository.existsLocalAccountByEmail(email)) {
                throw new EmailAlreadyRegisteredException("local account already exists for email");
            }
            Instant now = Instant.now(clock);
            User user = User.registerLocal(displayName, email, hashedPassword, now);
            return RegisterUserResult.from(userRepository.save(user));
        });
    }
}
