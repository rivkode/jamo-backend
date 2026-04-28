package app.backend.jamo.identity.application.event;

import app.backend.jamo.identity.domain.event.DisplayNameChanged;
import app.backend.jamo.identity.domain.repository.DisplayNameChangeRateLimiter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

/**
 * {@code DisplayNameChanged} 이벤트 핸들러 — Redis flag SETEX 호출.
 *
 * <p><b>실행 시점: AFTER_COMMIT</b> (decisions/identity/profile-app-infra-decisions.md §결정 #1).
 * RDB rollback 시 본 핸들러가 호출되지 않으므로 Redis flag 미반영. 정합성 안전.
 *
 * <p>commit 후 Redis 호출 실패 시 빈도 제한 미적용 — 사용자가 즉시 다시 변경 가능. 운영 정책상
 * 수용 가능 위험 (본 결정 박제). 운영 모니터링: Redis 응답 실패 메트릭.
 */
@Component
public class DisplayNameChangeRateLimiterListener {

    private final DisplayNameChangeRateLimiter rateLimiter;

    public DisplayNameChangeRateLimiterListener(DisplayNameChangeRateLimiter rateLimiter) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DisplayNameChanged event) {
        rateLimiter.markChanged(event.userId(), event.ttl());
    }
}
