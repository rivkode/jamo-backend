package app.backend.jamo.diary.infrastructure.redis;

import app.backend.jamo.common.auth.BlacklistChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link BlacklistChecker} 의 Redis 구현 — identity-service `SessionBlacklistRedisStore` 와 같은 키
 * schema (`bl:sid:{sessionId}`) 공유. read-only 호출 (EXISTS) 만 수행.
 *
 * <p><b>cross-service 키 공유 결정</b> (security-reviewer C1):
 * <ul>
 *   <li>identity-service 가 logout / refresh 회전 시 sid 를 blacklist 등록 (`SessionBlacklistRedisStore`)</li>
 *   <li>diary-service 의 access token verify hot path 가 본 checker 로 EXISTS 체크 → 등록 sid 즉시 거부</li>
 * </ul>
 *
 * <p>본 키 schema 변경은 identity-service 와 동시 변경 필수 — 박제
 * decisions/diary/sentence-feedback-presentation-decisions.md (cross-service Redis key 정책). 후속
 * common-infrastructure 모듈로 키 schema 상수 추출 검토.
 */
@Component
@RequiredArgsConstructor
public class SessionBlacklistRedisChecker implements BlacklistChecker {

    /** identity-service `SessionBlacklistRedisStore.KEY_PREFIX` 와 정합 — 변경 시 양 서비스 동시 변경. */
    private static final String KEY_PREFIX = "bl:sid:";

    private final StringRedisTemplate redis;

    @Override
    public boolean isBlacklisted(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + sessionId));
    }
}
