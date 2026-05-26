package app.backend.jamo.diary.presentation.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * 댓글 응답에 임베드되는 작성자 요약 (PRD 0526_flutter.md §3 Comment 스키마).
 *
 * <p>{@code username} 은 백엔드 도메인의 displayName 과 동의어 — 외부 명세 정합용 필드명.
 * {@code avatarUrl} 은 현 시점 미구현 도메인이라 항상 {@code null}.
 *
 * <p><b>null 직렬화 정책</b> (code-reviewer H1 / test-reviewer H1): {@code avatarUrl: null} 은 키 자체를
 * 응답에 포함한다 (Spring Boot Jackson 기본 {@code Include.ALWAYS}). PRD 0526_flutter.md §3 의
 * {@code "avatarUrl": null} 명시 노출과 정합. {@code spring.jackson.default-property-inclusion} 을
 * {@code non_null} 로 바꿀 경우 본 정책 회귀 — 변경 시 본 javadoc 갱신 필수.
 *
 * <p>UserSummary gRPC 응답 fallback 시 username 에 "(unknown)" 등이 들어올 수 있다 — Application 의
 * {@code UserSummaryView.displayNameOrUnknown} 결과 그대로 노출.
 *
 * @param userId    작성자 UUID
 * @param username  displayName 동의어 (PRD 명세 필드명)
 * @param avatarUrl 미구현 — 항상 null
 */
public record CommentAuthor(UUID userId, String username, String avatarUrl) {

    public CommentAuthor {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(username, "username");
    }
}
