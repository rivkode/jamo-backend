package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.SendValidationCodeCommand;
import app.backend.jamo.identity.domain.exception.ValidationRateLimitedException;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.ValidationCode;
import app.backend.jamo.identity.domain.repository.EmailSender;
import app.backend.jamo.identity.domain.repository.ValidationCodeStore;
import app.backend.jamo.identity.domain.repository.ValidationRateLimiter;
import app.backend.jamo.identity.infrastructure.config.EmailValidationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendValidationCodeServiceTest {

    private static final String EMAIL = "test@example.com";

    @Mock private ValidationCodeStore codeStore;
    @Mock private ValidationRateLimiter rateLimiter;
    @Mock private EmailSender emailSender;

    private EmailValidationProperties properties;
    private SendValidationCodeService service;

    @BeforeEach
    void setUp() {
        properties = new EmailValidationProperties(
                Duration.ofMinutes(5), 5,
                Duration.ofSeconds(30), 5,
                Duration.ofMinutes(10));
        service = new SendValidationCodeService(codeStore, rateLimiter, emailSender, properties);
    }

    @Test
    void send_success_records_then_issues_then_dispatches_with_same_code() {
        when(rateLimiter.canSend(any(Email.class))).thenReturn(true);
        ArgumentCaptor<ValidationCode> issuedCode = ArgumentCaptor.forClass(ValidationCode.class);
        ArgumentCaptor<ValidationCode> dispatchedCode = ArgumentCaptor.forClass(ValidationCode.class);

        service.send(new SendValidationCodeCommand(EMAIL));

        InOrder order = inOrder(rateLimiter, codeStore, emailSender);
        order.verify(rateLimiter).canSend(eq(new Email(EMAIL)));
        order.verify(rateLimiter).recordSend(eq(new Email(EMAIL)));
        order.verify(codeStore).issue(eq(new Email(EMAIL)), issuedCode.capture(), eq(Duration.ofMinutes(5)));
        order.verify(emailSender).sendValidationCode(eq(new Email(EMAIL)), dispatchedCode.capture());
        assertThat(dispatchedCode.getValue()).isEqualTo(issuedCode.getValue());
    }

    @Test
    void send_throws_rate_limited_when_canSend_returns_false() {
        when(rateLimiter.canSend(any(Email.class))).thenReturn(false);

        assertThatThrownBy(() -> service.send(new SendValidationCodeCommand(EMAIL)))
                .isInstanceOf(ValidationRateLimitedException.class);

        verify(rateLimiter, never()).recordSend(any(Email.class));
        verify(codeStore, never()).issue(any(), any(), any());
        verify(emailSender, never()).sendValidationCode(any(), any());
    }

    @Test
    void send_propagates_exception_when_emailSender_fails_after_store_already_issued() {
        when(rateLimiter.canSend(any(Email.class))).thenReturn(true);
        doThrow(new RuntimeException("smtp down"))
                .when(emailSender).sendValidationCode(any(Email.class), any(ValidationCode.class));

        assertThatThrownBy(() -> service.send(new SendValidationCodeCommand(EMAIL)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("smtp down");

        // try-then-record 정책: store 와 rate counter 는 이미 갱신된 상태로 잔존 (PRD §9 결정)
        verify(rateLimiter).recordSend(any(Email.class));
        verify(codeStore).issue(any(Email.class), any(ValidationCode.class), eq(Duration.ofMinutes(5)));
    }

    @Test
    void send_rejects_invalid_email_format_at_email_vo_before_any_port_call() {
        assertThatThrownBy(() -> service.send(new SendValidationCodeCommand("not-an-email")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(rateLimiter, never()).canSend(any());
        verify(codeStore, never()).issue(any(), any(), any());
        verify(emailSender, never()).sendValidationCode(any(), any());
    }

    @Test
    void send_rejects_blank_email_at_command_construction() {
        assertThatThrownBy(() -> new SendValidationCodeCommand(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SendValidationCodeCommand("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
