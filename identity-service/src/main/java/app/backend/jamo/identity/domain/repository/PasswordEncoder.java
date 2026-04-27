package app.backend.jamo.identity.domain.repository;

import app.backend.jamo.identity.domain.model.user.HashedPassword;

/**
 * 평문 비밀번호 → 해시 변환 port (PRD user/createUser.md §9 — BCrypt cost=12 결정).
 *
 * <p>Domain 은 어떤 해시 알고리즘인지 알지 않는다. Infrastructure 의 Spring Security
 * {@code BCryptPasswordEncoderAdapter} 가 본 인터페이스를 구현.
 *
 * <p>{@link #matches} 는 LOGIN use case (별도 PR) 에서 사용 — 본 PR (createUser) 은 {@link #encode} 만 호출.
 * 두 메서드를 한 port 에 두는 이유: 같은 알고리즘 / 비용 설정으로 묶이며, 운영 중 cost factor 변경 시
 * 한 곳만 갱신하면 일관성 유지 (Spring Security 표준 패턴 준용).
 */
public interface PasswordEncoder {

    HashedPassword encode(String rawPassword);

    boolean matches(String rawPassword, HashedPassword hashed);
}
