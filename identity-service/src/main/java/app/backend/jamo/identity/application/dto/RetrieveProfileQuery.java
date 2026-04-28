package app.backend.jamo.identity.application.dto;

import app.backend.jamo.identity.domain.model.user.UserId;

import java.util.Objects;

/**
 * 타 사용자 프로필 조회 query.
 *
 * <p>인증 검증 ({@code @LoginUser}) 은 Presentation 책임이므로 본 query 는 *조회 대상* userId 만 받는다 —
 * `loginUserId` 미사용 필드 제거 (Phase 6-b-c, code-reviewer M1 후속 박제). follow 도메인 도입 시
 * `viewerId` 필드 신규 추가 (시점에 결정).
 */
public record RetrieveProfileQuery(UserId userId) {

    public RetrieveProfileQuery {
        Objects.requireNonNull(userId, "userId");
    }
}
