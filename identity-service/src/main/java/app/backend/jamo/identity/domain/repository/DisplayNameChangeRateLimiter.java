package app.backend.jamo.identity.domain.repository;

import app.backend.jamo.identity.domain.model.user.UserId;

import java.time.Duration;

/**
 * displayName 변경 빈도 제한 port — 7일 1회 (PRD `profile/updateMyProfile.md` §1, decisions/identity/profile-prd-evaluation.md §결정 #3.1).
 *
 * <p><b>SoT 정합</b>: displayName 의 SoT 는 User aggregate 이므로 본 port 도 user 측 책임으로 분류 —
 * Redis flag key 는 {@code user:displayName_changed:{userId}} prefix 사용.
 *
 * <p>일반 흐름:
 * <ol>
 *   <li>{@link #check}: flag 존재 시 {@code DisplayNameChangeTooFrequentException} 던짐.</li>
 *   <li>{@code User.rename(newName, now)} 호출.</li>
 *   <li>{@link #markChanged}: TTL 7일 SETEX.</li>
 * </ol>
 *
 * <p><b>후속 검토</b>: {@code markChanged} 호출이 RDB 트랜잭션 내부 vs {@code @TransactionalEventListener(AFTER_COMMIT)}
 * 둘 중 어느 쪽인지는 Phase 6-b-b 슬라이스에서 결정 (decisions/identity/profile-prd-evaluation.md §후속 구현 검토).
 * 본 port 는 추상이므로 호출 위치는 어댑터 쪽 의사 선택.
 */
public interface DisplayNameChangeRateLimiter {

    /**
     * flag 존재 시 {@code DisplayNameChangeTooFrequentException} 을 던진다.
     */
    void check(UserId userId);

    /**
     * 변경 직후 호출 — TTL 동안 다음 변경 차단.
     */
    void markChanged(UserId userId, Duration ttl);
}
