package app.backend.jamo.identity.infrastructure.external;

import app.backend.jamo.identity.domain.model.user.HashedPassword;
import app.backend.jamo.identity.domain.repository.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Spring Security {@code BCryptPasswordEncoder} 기반 어댑터 (cost factor=12).
 *
 * <p>도메인 {@link PasswordEncoder} port 의 단일 구현. cost 값은 PRD user/createUser.md §9 결정
 * (BCrypt cost=12) 에 기반 — 운영 중 cost 변경 시 본 클래스만 갱신.
 *
 * <p>도메인의 {@link PasswordEncoder} 와 Spring Security 의 동명 클래스가 충돌하므로,
 * Spring 의 것은 사용 시점에 직접 인스턴스화하고 인터페이스 import 는 도메인쪽만 한다
 * (decisions/identity/local-credential-modeling.md 결정 참고).
 */
@Component
public class BCryptPasswordEncoderAdapter implements PasswordEncoder {

    private static final int BCRYPT_COST = 12;
    /**
     * BCrypt cost=12 dummy hash for timing equalization on missing LOCAL accounts.
     * The raw password intentionally does not matter; {@link #matchesDummy} ignores the result.
     */
    private static final String DUMMY_HASH =
            "$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6Ttxx6PGBesbhtPGE7PKwq6wDeFDC";

    private final BCryptPasswordEncoder delegate;

    public BCryptPasswordEncoderAdapter() {
        this.delegate = new BCryptPasswordEncoder(BCRYPT_COST);
    }

    @Override
    public HashedPassword encode(String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword");
        if (rawPassword.isEmpty()) {
            throw new IllegalArgumentException("rawPassword must not be empty");
        }
        return new HashedPassword(delegate.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, HashedPassword hashed) {
        Objects.requireNonNull(rawPassword, "rawPassword");
        Objects.requireNonNull(hashed, "hashed");
        return delegate.matches(rawPassword, hashed.value());
    }

    @Override
    public boolean matchesDummy(String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword");
        return delegate.matches(rawPassword, DUMMY_HASH);
    }
}
