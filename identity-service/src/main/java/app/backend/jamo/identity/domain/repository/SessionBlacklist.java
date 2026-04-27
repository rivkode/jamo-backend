package app.backend.jamo.identity.domain.repository;

import java.time.Duration;

/**
 * sessionId(sid) 단위 blacklist port.
 *
 * <p>logout 시 현재 sid 만 등록(`bl:sid:{sid}`), reuse detection 시 user 의 모든 sid 일괄 등록.
 * common-auth-jwt 의 {@code BlacklistChecker} 어댑터가 본 port 의 {@link #contains}
 * 를 호출해 access JWT 의 sid claim 을 거부한다 (ADR-0001 세부 정책 표).
 *
 * <ul>
 *   <li>{@link #blacklist} — TTL 은 <b>호출자(application service)</b> 가 산정해 전달한다.
 *       access JWT 잔여 수명 이상이어야 유실 없이 거부 보장 (예: {@code jwtProperties.accessTtl()}).
 *       구현체는 전달받은 TTL 을 그대로 SET EX 에 적용한다.</li>
 *   <li>{@link #contains} — JWT 검증 hot path 에서 호출되므로 O(1) Redis 조회 권장.</li>
 * </ul>
 *
 * <p>다른 도메인 모델({@code OAuthFlowSession}, {@code RefreshTokenRecord}) 이 {@code Instant expiresAt}
 * 을 쓰는 것과 달리 본 port 는 {@code Duration ttl} 시그니처를 사용한다 — 보관할 record VO 가 없는
 * 외부 효과 port 라 Redis 의 자연스러운 TTL 표현인 Duration 이 적합하다.
 */
public interface SessionBlacklist {

    void blacklist(String sessionId, Duration ttl);

    boolean contains(String sessionId);
}
