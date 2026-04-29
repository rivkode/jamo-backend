package app.backend.jamo.diary.domain.model.diarylike;

import java.util.Objects;
import java.util.UUID;

/**
 * DiaryLike Aggregate 식별자 (UUID 래핑 VO).
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §1 / §8.
 *
 * <p>DiaryLike 는 {@code Diary} 와 별도 Aggregate — 본 ID 는 {@code (diaryId, userId)} 유니크 제약과 함께 PK 역할.
 */
public record DiaryLikeId(UUID value) {

    public DiaryLikeId {
        Objects.requireNonNull(value, "value");
    }

    public static DiaryLikeId newId() {
        return new DiaryLikeId(UUID.randomUUID());
    }

    public static DiaryLikeId of(UUID value) {
        return new DiaryLikeId(value);
    }

    public static DiaryLikeId fromString(String value) {
        Objects.requireNonNull(value, "value");
        try {
            return new DiaryLikeId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid DiaryLikeId: " + value, e);
        }
    }

    public String asString() {
        return value.toString();
    }
}
