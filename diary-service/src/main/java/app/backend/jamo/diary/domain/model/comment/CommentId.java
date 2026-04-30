package app.backend.jamo.diary.domain.model.comment;

import java.util.Objects;
import java.util.UUID;

/**
 * Comment Aggregate 식별자 (UUID 래핑 VO).
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §1 (UUID 일관 — diary core / DiaryLike / sentence-feedback /
 * profile 정합).
 *
 * <p>{@code parentId} 는 같은 BC 안의 다른 Comment 식별자 — 외래 ID 가 아닌 동일 BC 내부 참조.
 * Aggregate 간 직접 참조는 금지 (Vernon 12장) 이므로 {@link CommentId} 만 보유 (raw UUID 아님).
 */
public record CommentId(UUID value) {

    public CommentId {
        Objects.requireNonNull(value, "value");
    }

    public static CommentId newId() {
        return new CommentId(UUID.randomUUID());
    }

    public static CommentId of(UUID value) {
        return new CommentId(value);
    }

    public static CommentId fromString(String value) {
        Objects.requireNonNull(value, "value");
        try {
            return new CommentId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid CommentId: " + value, e);
        }
    }

    public String asString() {
        return value.toString();
    }
}
