package app.backend.jamo.identity.domain.event;

import app.backend.jamo.identity.domain.model.user.UserId;

import java.time.Duration;
import java.util.Objects;

/**
 * `User.displayName` 변경 직후 발행되는 도메인 이벤트.
 *
 * <p><b>본 이벤트는 Spring `ApplicationEventPublisher` 의 *메모리 이벤트* — Kafka 미경유</b>.
 * 서비스 내부 부수효과 트리거 (Redis flag SETEX) 만 담당. cross-service 전달 (예: platform-service
 * Read Model 동기화) 은 본 이벤트의 책임이 아니다 — 그 용도는 {@code UserSummaryService} gRPC
 * 동기 호출 또는 향후 별도 Outbox 이벤트.
 *
 * <p><b>handler</b>: {@code DisplayNameChangeRateLimiterListener} — {@code @TransactionalEventListener(AFTER_COMMIT)}
 * 으로 RDB rollback 시 Redis 미반영. (decisions/identity/profile-app-infra-decisions.md §결정 #1)
 */
public record DisplayNameChanged(UserId userId, Duration ttl) {

    public DisplayNameChanged {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }
}
