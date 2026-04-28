package app.backend.jamo.identity.application.dto;

import app.backend.jamo.identity.domain.model.user.UserId;

import java.util.Objects;

/**
 * 본인 프로필 조회 query — `@LoginUser` 가 인증한 사용자의 userId 만 필요.
 */
public record RetrieveMyProfileQuery(UserId userId) {

    public RetrieveMyProfileQuery {
        Objects.requireNonNull(userId, "userId");
    }
}
