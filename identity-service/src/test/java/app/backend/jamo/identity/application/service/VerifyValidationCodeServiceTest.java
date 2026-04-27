package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.VerifyValidationCodeCommand;
import app.backend.jamo.identity.domain.exception.ValidationCodeExpiredException;
import app.backend.jamo.identity.domain.exception.ValidationCodeLockedException;
import app.backend.jamo.identity.domain.exception.ValidationCodeMismatchException;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.ValidationCode;
import app.backend.jamo.identity.domain.repository.EmailValidatedFlag;
import app.backend.jamo.identity.domain.repository.ValidationCodeStore;
import app.backend.jamo.identity.infrastructure.config.EmailValidationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerifyValidationCodeServiceTest {

    private static final String EMAIL = "test@example.com";
    private static final String STORED_CODE = "123456";
    private static final String WRONG_CODE = "654321";

    @Mock private ValidationCodeStore codeStore;
    @Mock private EmailValidatedFlag validatedFlag;

    private EmailValidationProperties properties;
    private VerifyValidationCodeService service;

    @BeforeEach
    void setUp() {
        properties = new EmailValidationProperties(
                Duration.ofMinutes(5), 5,
                Duration.ofSeconds(30), 5,
                Duration.ofMinutes(10));
        service = new VerifyValidationCodeService(codeStore, validatedFlag, properties);
    }

    @Test
    void verify_success_invalidates_code_and_marks_flag() {
        when(codeStore.find(any(Email.class))).thenReturn(Optional.of(ValidationCode.of(STORED_CODE)));

        assertThatCode(() -> service.verify(new VerifyValidationCodeCommand(EMAIL, STORED_CODE)))
                .doesNotThrowAnyException();

        verify(codeStore).invalidate(eq(new Email(EMAIL)));
        verify(validatedFlag).mark(eq(new Email(EMAIL)), eq(Duration.ofMinutes(10)));
        verify(codeStore, never()).incrementAttempts(any(Email.class));
    }

    @Test
    void verify_throws_expired_when_no_stored_code() {
        when(codeStore.find(any(Email.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(new VerifyValidationCodeCommand(EMAIL, WRONG_CODE)))
                .isInstanceOf(ValidationCodeExpiredException.class);

        verify(codeStore, never()).incrementAttempts(any());
        verify(codeStore, never()).invalidate(any());
        verify(validatedFlag, never()).mark(any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void verify_throws_mismatch_for_attempts_under_max(int currentAttempts) {
        when(codeStore.find(any(Email.class))).thenReturn(Optional.of(ValidationCode.of(STORED_CODE)));
        when(codeStore.incrementAttempts(any(Email.class))).thenReturn(currentAttempts);

        assertThatThrownBy(() -> service.verify(new VerifyValidationCodeCommand(EMAIL, WRONG_CODE)))
                .isInstanceOf(ValidationCodeMismatchException.class);

        verify(codeStore, never()).invalidate(any());
        verify(validatedFlag, never()).mark(any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 6, 7})
    void verify_throws_locked_at_or_above_max_attempts_and_invalidates(int currentAttempts) {
        when(codeStore.find(any(Email.class))).thenReturn(Optional.of(ValidationCode.of(STORED_CODE)));
        when(codeStore.incrementAttempts(any(Email.class))).thenReturn(currentAttempts);

        assertThatThrownBy(() -> service.verify(new VerifyValidationCodeCommand(EMAIL, WRONG_CODE)))
                .isInstanceOf(ValidationCodeLockedException.class);

        verify(codeStore).invalidate(eq(new Email(EMAIL)));
        verify(validatedFlag, never()).mark(any(), any());
    }

    @Test
    void verify_locked_exception_message_does_not_leak_attempts_count() {
        when(codeStore.find(any(Email.class))).thenReturn(Optional.of(ValidationCode.of(STORED_CODE)));
        when(codeStore.incrementAttempts(any(Email.class))).thenReturn(5);

        assertThatThrownBy(() -> service.verify(new VerifyValidationCodeCommand(EMAIL, WRONG_CODE)))
                .isInstanceOf(ValidationCodeLockedException.class)
                .hasMessageNotContaining("5")
                .hasMessageNotContaining("attempt");
    }

    @Test
    void verify_rejects_invalid_code_format_at_value_object() {
        assertThatThrownBy(() -> service.verify(new VerifyValidationCodeCommand(EMAIL, "12345")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(codeStore, never()).find(any());
        verify(codeStore, never()).incrementAttempts(any());
    }

    @Test
    void verify_rejects_blank_code_at_command_construction() {
        assertThatThrownBy(() -> new VerifyValidationCodeCommand(EMAIL, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
