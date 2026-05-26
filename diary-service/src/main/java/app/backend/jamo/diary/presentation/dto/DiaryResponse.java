package app.backend.jamo.diary.presentation.dto;

import app.backend.jamo.diary.application.dto.diary.DiaryView;
import app.backend.jamo.diary.domain.model.diary.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

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
 *
 * <p><b>PRD 0526_flutter.md §2 정합 (Slice 2)</b>:
 * <ul>
 *   <li>{@code isPublic} alias ({@code visibility == "PUBLIC"}) — frontend 의 boolean 단순화 필드.</li>
 *   <li>{@code userLiked} alias — {@code likedByMe} 동의어.</li>
 *   <li>{@code author} 객체 alias — {userId, username, avatarUrl}. frontend 가 객체 형태로 접근 가능.</li>
 * </ul>
 * 기존 평탄 필드 ({@code visibility}, {@code likedByMe}, {@code authorId}, {@code authorDisplayName}) 도
 * 그대로 노출 — 양방향 호환.
 *
 * <p><b>SoT 정책 (alias 제거 추적)</b>: 정식 SoT 는 평탄 필드 ({@code visibility} / {@code likedByMe} /
 * {@code authorId} / {@code authorDisplayName}) 다. 신규 클라이언트는 평탄 필드를 사용한다. {@code author}
 * 객체 / {@code isPublic} / {@code userLiked} 는 일시적 호환 alias — frontend 전환 후 deprecation 단계로
 * 진입한다. 박제: {@code docs/decisions/api/spec-alias-removal-plan.md}.
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

    /** PRD §2 alias — visibility=="PUBLIC" 의 boolean 단순화. enum name() 참조로 IDE refactor 안전. */
    @JsonProperty("isPublic")
    public boolean isPublic() {
        return Visibility.PUBLIC.name().equals(visibility);
    }

    /** PRD §2 alias — likedByMe 동의어. */
    @JsonProperty("userLiked")
    public boolean userLiked() {
        return likedByMe;
    }

    /** PRD §2 alias — {userId, username, avatarUrl} 객체. avatarUrl 은 도메인 미구현이라 null. */
    @JsonProperty("author")
    public DiaryAuthor author() {
        return new DiaryAuthor(authorId, authorDisplayName, null);
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
