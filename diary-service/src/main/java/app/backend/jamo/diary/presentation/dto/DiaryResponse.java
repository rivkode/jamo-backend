package app.backend.jamo.diary.presentation.dto;

import app.backend.jamo.diary.application.dto.diary.DiaryView;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 일기 단건 응답 (POST /diaries, GET /diaries/{id}).
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §4 (응답 schema 11 필드).
 *
 * <p>create / get 모두 동일 schema. {@code visibility} 는 enum 이름 ("PUBLIC" / "PRIVATE") 직렬화.
 *
 * <p>{@code authorDisplayName} 은 UserSummaryService gRPC 응답 fallback 후 ("(unknown)" 등) 가 들어올 수
 * 있다 — Application 의 {@link app.backend.jamo.diary.application.port.UserSummaryView#displayNameOrUnknown}
 * 결과.
 */
public record DiaryResponse(
    UUID diaryId,
    UUID authorId,
    String authorDisplayName,
    String content,
    List<String> images,
    List<String> tags,
    String visibility,
    int likeCount,
    int commentCount,
    boolean likedByMe,
    Instant createdAt
) {

    public DiaryResponse {
        Objects.requireNonNull(diaryId, "diaryId");
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(authorDisplayName, "authorDisplayName");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(images, "images");
        Objects.requireNonNull(tags, "tags");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(createdAt, "createdAt");
        images = List.copyOf(images);
        tags = List.copyOf(tags);
    }

    public static DiaryResponse from(DiaryView view) {
        return new DiaryResponse(
            view.diaryId(),
            view.authorId(),
            view.authorDisplayName(),
            view.content(),
            view.images(),
            view.tags(),
            view.visibility().name(),
            view.likeCount(),
            view.commentCount(),
            view.likedByMe(),
            view.createdAt()
        );
    }
}
