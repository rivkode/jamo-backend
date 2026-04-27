package app.backend.jamo.identity.presentation.dto;

import app.backend.jamo.identity.application.dto.RegisterUserResult;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * POST /api/v1/users 의 Response body — 자동 로그인 미적용 (PRD user/createUser.md §9).
 *
 * <p>토큰 미발급. 식별 정보만 반환 — 클라이언트는 가입 후 별도 LOCAL 로그인 호출 (별도 PR).
 */
public record RegisterUserResponse(UUID userId, String email, String displayName, Instant createdAt) {

    public RegisterUserResponse {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static RegisterUserResponse from(RegisterUserResult result) {
        return new RegisterUserResponse(
                result.userId(), result.email(), result.displayName(), result.createdAt());
    }
}
