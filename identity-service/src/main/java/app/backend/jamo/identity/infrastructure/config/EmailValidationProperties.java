package app.backend.jamo.identity.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 이메일 검증 흐름 정책 (PRD user/sendValidationNumber.md §9 + user/validateEmail.md §9).
 *
 * <ul>
 *   <li>{@link #codeTtl} — 코드 + attempts 카운터 라이프사이클. 양수 강제.</li>
 *   <li>{@link #maxAttempts} — 동일 코드 검증 시도 한도. 도달 시 코드 invalidate. 양수 강제.</li>
 *   <li>{@link #cooldown} — 동일 email 발송 간 최소 간격. {@code Duration.ZERO} 도 허용
 *       (테스트 환경에서 cooldown 비활성화 — daily-limit 만으로 spam 방어). 운영에서는
 *       반드시 양수 권장.</li>
 *   <li>{@link #dailyLimit} — 동일 email 24h 내 발송 한도. 양수 강제. 운영 default 5
 *       (decision: email-validation-deployment-checklist.md M1).</li>
 *   <li>{@link #validatedFlagTtl} — createUser 사전조건 flag 의 TTL. 양수 강제.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "jamo.user-validation")
public record EmailValidationProperties(
        Duration codeTtl,
        int maxAttempts,
        Duration cooldown,
        int dailyLimit,
        Duration validatedFlagTtl
) {

    public EmailValidationProperties {
        if (codeTtl == null || codeTtl.isZero() || codeTtl.isNegative()) {
            throw new IllegalArgumentException("codeTtl must be positive");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (cooldown == null || cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown must be zero or positive");
        }
        if (dailyLimit <= 0) {
            throw new IllegalArgumentException("dailyLimit must be positive");
        }
        if (validatedFlagTtl == null || validatedFlagTtl.isZero() || validatedFlagTtl.isNegative()) {
            throw new IllegalArgumentException("validatedFlagTtl must be positive");
        }
    }
}
