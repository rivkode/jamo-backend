package app.backend.jamo.identity.domain.repository;

import app.backend.jamo.identity.domain.model.user.Email;

import java.time.Duration;

/**
 * 이메일 검증 완료 flag port — `createUser` 의 사전조건 (소비형).
 *
 * <p>validateEmail 성공 시 {@link #mark} 호출 → TTL 10분 동안 createUser 가 호출 가능
 * (PRD user/validateEmail.md §9, user/createUser.md §9). createUser 진입 시 {@link #consume}
 * 로 flag 존재 여부 확인 + 즉시 삭제 (소비형 — 같은 이메일로 재가입 시도 방지).
 *
 * <p>다른 도메인 모델({@code RefreshTokenRecord}) 이 {@code Instant expiresAt} 을 쓰는 것과 달리
 * 본 port 는 {@code Duration ttl} 시그니처를 사용한다 — 보관할 record VO 가 없는 외부 효과
 * port 라 Redis 의 자연스러운 TTL 표현인 Duration 이 적합 (SessionBlacklist 패턴과 동일).
 */
public interface EmailValidatedFlag {

    void mark(Email email, Duration ttl);

    boolean consume(Email email);
}
