package app.backend.jamo.identity.domain.model.user;

import java.util.Objects;

/**
 * BCrypt 등으로 이미 해시된 비밀번호 — Domain 은 평문(raw)을 절대 보관/노출하지 않는다.
 *
 * <p>해싱 자체는 {@link app.backend.jamo.identity.domain.repository.PasswordEncoder} port 의
 * Infrastructure 어댑터 (Spring Security {@code BCryptPasswordEncoder}, cost=12) 책임이며,
 * 본 VO 는 그 결과 문자열을 도메인 식별 가능한 타입으로 래핑.
 *
 * <p>{@code toString()} 은 {@link Object} 기본 구현을 사용하지 않고 마스킹된 표현을 반환 —
 * 로그/스택트레이스에 해시값이 노출되는 사고 방지 (PRD §8 비밀번호 해싱 알고리즘 결정의 일부).
 */
public record HashedPassword(String value) {

    public HashedPassword {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("hashed password must not be blank");
        }
    }

    @Override
    public String toString() {
        return "HashedPassword[***]";
    }
}
