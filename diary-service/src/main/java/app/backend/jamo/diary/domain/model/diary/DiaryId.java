package app.backend.jamo.diary.domain.model.diary;

import java.util.Objects;
import java.util.UUID;

/**
 * Diary Aggregate 식별자 (UUID 래핑 VO).
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §1 (UUID 일관 — profile / comment / sentence-feedback 정합).
 *
 * <p>{@code authorId} 는 외래 BC (identity-service) Aggregate ID 이므로 raw {@link UUID} 보유 (ADR-0005 / sentence-feedback 정합).
 */
public record DiaryId(UUID value) {

    public DiaryId {
        Objects.requireNonNull(value, "value");
    }

    public static DiaryId newId() {
        return new DiaryId(UUID.randomUUID());
    }

    public static DiaryId of(UUID value) {
        return new DiaryId(value);
    }

    public static DiaryId fromString(String value) {
        Objects.requireNonNull(value, "value");
        try {
            return new DiaryId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid DiaryId: " + value, e);
        }
    }

    public String asString() {
        return value.toString();
    }
}
