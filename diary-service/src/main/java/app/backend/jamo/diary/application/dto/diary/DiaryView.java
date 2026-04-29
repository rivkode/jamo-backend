package app.backend.jamo.diary.application.dto.diary;

import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.Visibility;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 일기 응답 view (11 필드, 박제 §4).
 *
 * <p>create / get / listFeed / listMyFeed 모두 동일 schema. 작성 직후 likeCount=0 / commentCount=0 /
 * likedByMe=false.
 *
 * <p>{@code authorDisplayName} 은 UserSummary gRPC 응답으로 조립 (외래 BC). NOT_FOUND / fallback 시
 * 호출자가 적절한 기본값 ("(unknown)") 결정.
 */
public record DiaryView(
    UUID diaryId,
    UUID authorId,
    String authorDisplayName,
    String content,
    List<String> images,
    List<String> tags,
    Visibility visibility,
    int likeCount,
    int commentCount,
    boolean likedByMe,
    Instant createdAt
) {

    public DiaryView {
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

    /**
     * Diary aggregate + viewer-context (likedByMe) + UserSummary (authorDisplayName) 조립.
     *
     * @param authorDisplayName fallback 처리는 호출자가 (UserSummary NOT_FOUND 시 e.g. "(unknown)")
     */
    public static DiaryView from(Diary diary, String authorDisplayName, boolean likedByMe) {
        return new DiaryView(
            diary.id().value(),
            diary.authorId(),
            authorDisplayName,
            diary.content().value(),
            diary.images().values(),
            diary.tags().asStrings(),
            diary.visibility(),
            diary.likeCount(),
            diary.commentCount(),
            likedByMe,
            diary.createdAt()
        );
    }
}
