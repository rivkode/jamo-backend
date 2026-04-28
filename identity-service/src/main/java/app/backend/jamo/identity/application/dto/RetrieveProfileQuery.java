package app.backend.jamo.identity.application.dto;

import app.backend.jamo.identity.domain.model.user.UserId;

import java.util.Objects;

/**
 * 타 사용자 프로필 조회 query.
 *
 * <p>{@code loginUserId} 는 인증 검증을 위해 받지만, 응답 합성에는 미사용 — viewer-context (follow 여부)
 * 는 follow 도메인 부재로 Non-Goal (decisions/identity/profile-prd-evaluation.md).
 */
public record RetrieveProfileQuery(UserId loginUserId, UserId userId) {

    public RetrieveProfileQuery {
        Objects.requireNonNull(loginUserId, "loginUserId");
        Objects.requireNonNull(userId, "userId");
    }
}
