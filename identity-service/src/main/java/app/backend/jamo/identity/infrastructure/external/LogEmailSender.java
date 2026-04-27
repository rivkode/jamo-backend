package app.backend.jamo.identity.infrastructure.external;

import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.ValidationCode;
import app.backend.jamo.identity.domain.repository.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@link EmailSender} 의 stub 구현 — 검증코드를 로그로만 출력 ({@code local/dev/test} profile 한정).
 *
 * <p>운영(`prod`) profile 에서는 본 빈이 활성화되지 않는다 — 검증코드 평문 로그 노출 차단
 * (security review H1, A09 Sensitive Data Exposure). 운영 SMTP/SES 어댑터는 별도 PR
 * 에서 도입하며, 그 어댑터는 별도 profile 또는 {@code @ConditionalOnProperty} 로 분기.
 *
 * <p>운영 배포 전 체크리스트는
 * {@code docs/decisions/identity/email-validation-deployment-checklist.md} 참조.
 */
@Component
@Profile({"local", "dev", "test", "e2e"})
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public void sendValidationCode(Email email, ValidationCode code) {
        log.info("[stub] validation code email — to={} code={}",
                maskEmail(email), code.value());
    }

    private static String maskEmail(Email email) {
        String value = email.value();
        int at = value.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? value.substring(at) : "");
        }
        return value.charAt(0) + "***" + value.substring(at);
    }
}
