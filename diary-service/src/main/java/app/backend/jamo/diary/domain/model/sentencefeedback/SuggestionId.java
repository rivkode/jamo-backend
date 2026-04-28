package app.backend.jamo.diary.domain.model.sentencefeedback;

import java.util.Objects;
import java.util.UUID;

/**
 * Suggestion VO 의 식별자 (UUID 래핑).
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §1 (PRD `String` → UUID FIX).
 * AR 내부에서 {@code accept(suggestionId)} 매칭에 사용 — 외부 식별성 아닌 AR 내부 식별성.
 */
public record SuggestionId(UUID value) {

    public SuggestionId {
        Objects.requireNonNull(value, "value");
    }

    public static SuggestionId newId() {
        return new SuggestionId(UUID.randomUUID());
    }

    public static SuggestionId of(UUID value) {
        return new SuggestionId(value);
    }

    public static SuggestionId fromString(String value) {
        Objects.requireNonNull(value, "value");
        try {
            return new SuggestionId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid SuggestionId: " + value, e);
        }
    }

    public String asString() {
        return value.toString();
    }
}
