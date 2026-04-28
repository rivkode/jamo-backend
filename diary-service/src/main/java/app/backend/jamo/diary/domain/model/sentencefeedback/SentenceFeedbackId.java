package app.backend.jamo.diary.domain.model.sentencefeedback;

import java.util.Objects;
import java.util.UUID;

/**
 * SentenceFeedback Aggregate 식별자 (UUID 래핑 VO).
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §1 (UUID 일관).
 * Profile / User aggregate VO 의 factory 패턴 (newId / of / fromString) 정합.
 */
public record SentenceFeedbackId(UUID value) {

    public SentenceFeedbackId {
        Objects.requireNonNull(value, "value");
    }

    /** 신규 ID 생성. */
    public static SentenceFeedbackId newId() {
        return new SentenceFeedbackId(UUID.randomUUID());
    }

    public static SentenceFeedbackId of(UUID value) {
        return new SentenceFeedbackId(value);
    }

    public static SentenceFeedbackId fromString(String value) {
        Objects.requireNonNull(value, "value");
        try {
            return new SentenceFeedbackId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid SentenceFeedbackId: " + value, e);
        }
    }

    public String asString() {
        return value.toString();
    }
}
