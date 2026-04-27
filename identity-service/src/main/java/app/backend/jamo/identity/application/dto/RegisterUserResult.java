package app.backend.jamo.identity.application.dto;

import app.backend.jamo.identity.domain.model.user.User;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * LOCAL 회원가입 결과 — 자동 로그인 미적용 (PRD user/createUser.md §9 FIX).
 *
 * <p>토큰 발급 없이 식별 정보만 반환. presentation 의 {@code RegisterUserResponse} 가 본 record
 * 의 필드를 그대로 직렬화 — Application 이 Domain 객체를 노출하지 않도록 분리.
 */
public record RegisterUserResult(UUID userId, String email, String displayName, Instant createdAt) {

    public RegisterUserResult {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static RegisterUserResult from(User user) {
        return new RegisterUserResult(
                user.id().value(),
                user.email().orElseThrow(() ->
                        new IllegalStateException("LOCAL user must have email")).value(),
                user.displayName().value(),
                user.createdAt()
        );
    }
}
