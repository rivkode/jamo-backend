package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.RegisterUserCommand;
import app.backend.jamo.identity.application.dto.RegisterUserResult;
import app.backend.jamo.identity.domain.exception.EmailAlreadyRegisteredException;
import app.backend.jamo.identity.domain.exception.EmailNotValidatedException;
import app.backend.jamo.identity.domain.model.user.AccountType;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.HashedPassword;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.repository.EmailValidatedFlag;
import app.backend.jamo.identity.domain.repository.PasswordEncoder;
import app.backend.jamo.identity.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-28T10:00:00Z");

    private EmailValidatedFlag emailValidatedFlag;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private TransactionTemplate transactionTemplate;
    private RegisterUserService service;

    @BeforeEach
    void setUp() {
        emailValidatedFlag = mock(EmailValidatedFlag.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        // TransactionTemplate stub — execute(callback) 가 callback 을 그대로 실행 (트랜잭션 없는 단위 테스트)
        transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new RegisterUserService(emailValidatedFlag, userRepository, passwordEncoder,
                transactionTemplate, clock);
    }

    @Test
    void register_happy_path_consumes_flag_encodes_password_and_saves_user() {
        Email email = new Email("user@jamoai.app");
        when(emailValidatedFlag.consume(email)).thenReturn(true);
        when(userRepository.existsLocalAccountByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode("PlainPa$$w0rd")).thenReturn(new HashedPassword("$2a$12$encoded"));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterUserResult result = service.register(
                new RegisterUserCommand("user@jamoai.app", "PlainPa$$w0rd", "jamo"));

        // 순서 검증: consume → encode → exists → save
        // (encode 는 트랜잭션 외부, exists+save 는 트랜잭션 내부 — H2 fix)
        InOrder order = inOrder(emailValidatedFlag, passwordEncoder, userRepository);
        order.verify(emailValidatedFlag).consume(email);
        order.verify(passwordEncoder).encode("PlainPa$$w0rd");
        order.verify(userRepository).existsLocalAccountByEmail(email);
        order.verify(userRepository).save(any(User.class));

        // 저장된 aggregate 의 상태
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.accountType()).isEqualTo(AccountType.LOCAL);
        assertThat(saved.email()).isPresent();
        assertThat(saved.email().get().value()).isEqualTo("user@jamoai.app");
        assertThat(saved.hashedPassword()).isPresent();
        assertThat(saved.hashedPassword().get().value()).isEqualTo("$2a$12$encoded");
        assertThat(saved.displayName().value()).isEqualTo("jamo");
        assertThat(saved.createdAt()).isEqualTo(NOW);

        // 결과 DTO
        assertThat(result.userId()).isEqualTo(saved.id().value());
        assertThat(result.email()).isEqualTo("user@jamoai.app");
        assertThat(result.displayName()).isEqualTo("jamo");
        assertThat(result.createdAt()).isEqualTo(NOW);
    }

    @Test
    void register_rejects_when_email_validation_flag_missing() {
        Email email = new Email("user@jamoai.app");
        when(emailValidatedFlag.consume(email)).thenReturn(false);

        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand("user@jamoai.app", "PlainPa$$w0rd", "jamo")))
                .isInstanceOf(EmailNotValidatedException.class);

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).existsLocalAccountByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_rejects_when_local_account_already_exists() {
        Email email = new Email("user@jamoai.app");
        when(emailValidatedFlag.consume(email)).thenReturn(true);
        when(passwordEncoder.encode("PlainPa$$w0rd")).thenReturn(new HashedPassword("$2a$12$encoded"));
        when(userRepository.existsLocalAccountByEmail(email)).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand("user@jamoai.app", "PlainPa$$w0rd", "jamo")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_consumes_flag_before_password_encode_and_db_check() {
        // PRD §9 의도: flag 가 가장 먼저 consume — exists/save 는 다른 사유로 실패해도 flag 는 이미 소비.
        Email email = new Email("user@jamoai.app");
        when(emailValidatedFlag.consume(email)).thenReturn(true);
        when(passwordEncoder.encode("PlainPa$$w0rd")).thenReturn(new HashedPassword("$2a$12$encoded"));
        when(userRepository.existsLocalAccountByEmail(email)).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand("user@jamoai.app", "PlainPa$$w0rd", "jamo")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        InOrder order = inOrder(emailValidatedFlag, passwordEncoder, userRepository);
        order.verify(emailValidatedFlag).consume(eq(email));
        order.verify(passwordEncoder).encode("PlainPa$$w0rd");
        order.verify(userRepository).existsLocalAccountByEmail(eq(email));

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_rejects_invalid_email_format_before_any_side_effect() {
        // VO 생성 단계에서 IllegalArgumentException — flag / encode / DB 모두 호출되지 않음
        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand("not-an-email", "PlainPa$$w0rd", "jamo")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid email format");

        verify(emailValidatedFlag, never()).consume(any());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).existsLocalAccountByEmail(any());
        verify(userRepository, never()).save(any());
    }
}
