package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.SendValidationCodeCommand;
import app.backend.jamo.identity.domain.exception.ValidationRateLimitedException;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.ValidationCode;
import app.backend.jamo.identity.domain.repository.EmailSender;
import app.backend.jamo.identity.domain.repository.ValidationCodeStore;
import app.backend.jamo.identity.domain.repository.ValidationRateLimiter;
import app.backend.jamo.identity.infrastructure.config.EmailValidationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 이메일 검증코드 발송 use case (PRD user/sendValidationNumber.md §9).
 *
 * <p>흐름: rate check → recordSend → 코드 generate → store issue → 외부 발송. recordSend 를
 * 발송 직전에 호출해 발송 실패 시도도 spam 카운터에 포함시키는 보수적 정책 (decision:
 * try-then-record). 발송 자체 실패는 {@link EmailSender} 어댑터가 던지는 예외로 surfacing
 * 되며, 그 시점엔 store 에 코드가 이미 남아있으므로 사용자 재요청 시 30s 쿨다운 안내 + 5분
 * 후 자연 만료.
 *
 * <p>본 service 는 Redis 만 다루므로 {@code @Transactional} 미보유 — 부분 실패는 application
 * 흐름에서 best-effort 처리.
 */
@Service
@RequiredArgsConstructor
public class SendValidationCodeService {

    private final ValidationCodeStore codeStore;
    private final ValidationRateLimiter rateLimiter;
    private final EmailSender emailSender;
    private final EmailValidationProperties properties;

    public void send(SendValidationCodeCommand command) {
        Email email = new Email(command.email());

        if (!rateLimiter.canSend(email)) {
            throw new ValidationRateLimitedException("rate limit exceeded for email send");
        }
        rateLimiter.recordSend(email);

        ValidationCode code = ValidationCode.generate();
        codeStore.issue(email, code, properties.codeTtl());
        emailSender.sendValidationCode(email, code);
    }
}
