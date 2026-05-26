package app.backend.jamo.identity.presentation.dto;

import app.backend.jamo.identity.application.dto.RegisterUserResult;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * POST /api/v1/users 의 Response body — 자동 로그인 미적용 (PRD user/createUser.md §9).
 *
 * <p>토큰 미발급. 식별 정보만 반환 — 클라이언트는 가입 후 별도 LOCAL 로그인 호출 (별도 PR).
 *
 * <p><b>PRD 0526_flutter.md §1.2 정합 (Slice 2)</b>: {@code username} alias 동시 노출 (값은
 * {@code displayName} 동일). 백엔드 도메인 용어는 {@code displayName} 이 의미상 정확하나, frontend
 * PRD 가 {@code username} 명세 — 양쪽 모두 응답에 포함.
 */
public record RegisterUserResponse(UUID userId, String email, String displayName, Instant createdAt) {

    public RegisterUserResponse {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** PRD §1.2 alias — frontend 호환 필드명. */
    @JsonProperty("username")
    public String username() {
        return displayName;
    }

    public static RegisterUserResponse from(RegisterUserResult result) {
        return new RegisterUserResponse(
                result.userId(), result.email(), result.displayName(), result.createdAt());
    }
}
