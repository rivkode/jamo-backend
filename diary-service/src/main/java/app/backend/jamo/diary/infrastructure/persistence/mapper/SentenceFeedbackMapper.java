package app.backend.jamo.diary.infrastructure.persistence.mapper;

import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedback;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedbackId;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceText;
import app.backend.jamo.diary.domain.model.sentencefeedback.Status;
import app.backend.jamo.diary.domain.model.sentencefeedback.Suggestion;
import app.backend.jamo.diary.domain.model.sentencefeedback.SuggestionId;
import app.backend.jamo.diary.domain.model.sentencefeedback.Tone;
import app.backend.jamo.diary.infrastructure.persistence.entity.SentenceFeedbackJpaEntity;
import app.backend.jamo.diary.infrastructure.persistence.entity.SentenceFeedbackJpaEntity.SuggestionEmbedded;

import java.util.List;

/**
 * SentenceFeedback Aggregate ↔ JpaEntity 변환.
 *
 * <p>ProfileMapper 패턴 정합 — toJpaEntity / mergeInto / toDomain 3 메서드 + Suggestion ↔ SuggestionEmbedded
 * 변환 helper.
 */
public final class SentenceFeedbackMapper {

    private SentenceFeedbackMapper() {
    }

    public static SentenceFeedbackJpaEntity toJpaEntity(SentenceFeedback feedback) {
        return new SentenceFeedbackJpaEntity(
            feedback.id().value(),
            feedback.userId(),
            feedback.diaryId().orElse(null),
            feedback.originalSentence().value(),
            feedback.tone().map(Enum::name).orElse(null),
            feedback.status().name(),
            toEmbedded(feedback.suggestions()),
            feedback.decisionSuggestionId().map(SuggestionId::value).orElse(null),
            feedback.rejectionReason().orElse(null),
            feedback.failureReason().orElse(null),
            feedback.expiresAt().orElse(null),
            feedback.decidedAt().orElse(null),
            feedback.createdAt()
        );
    }

    /**
     * 기존 row 갱신용 — JPA managed entity 의 mutable setter 호출. 불변 식별자
     * ({@code id} / {@code userId} / {@code diaryId} / {@code originalSentence} / {@code tone} /
     * {@code createdAt}) 는 변경 X.
     */
    public static SentenceFeedbackJpaEntity mergeInto(SentenceFeedbackJpaEntity existing,
                                                      SentenceFeedback feedback) {
        existing.setStatus(feedback.status().name());
        existing.setSuggestions(toEmbedded(feedback.suggestions()));
        existing.setDecisionSuggestionId(feedback.decisionSuggestionId().map(SuggestionId::value).orElse(null));
        existing.setRejectionReason(feedback.rejectionReason().orElse(null));
        existing.setFailureReason(feedback.failureReason().orElse(null));
        existing.setExpiresAt(feedback.expiresAt().orElse(null));
        existing.setDecidedAt(feedback.decidedAt().orElse(null));
        return existing;
    }

    public static SentenceFeedback toDomain(SentenceFeedbackJpaEntity entity) {
        return SentenceFeedback.reconstitute(
            SentenceFeedbackId.of(entity.getId()),
            entity.getUserId(),
            entity.getDiaryId(),
            new SentenceText(entity.getOriginalSentence()),
            entity.getTone() == null ? null : Tone.valueOf(entity.getTone()),
            Status.valueOf(entity.getStatus()),
            toDomainSuggestions(entity.getSuggestions()),
            entity.getDecisionSuggestionId() == null ? null : SuggestionId.of(entity.getDecisionSuggestionId()),
            entity.getRejectionReason(),
            entity.getFailureReason(),
            entity.getExpiresAt(),
            entity.getDecidedAt(),
            entity.getCreatedAt()
        );
    }

    private static List<SuggestionEmbedded> toEmbedded(List<Suggestion> suggestions) {
        if (suggestions == null) {
            return List.of();
        }
        return suggestions.stream()
            .map(s -> new SuggestionEmbedded(s.id().value(), s.text(), s.reason(), s.confidence()))
            .toList();
    }

    private static List<Suggestion> toDomainSuggestions(List<SuggestionEmbedded> embedded) {
        if (embedded == null) {
            return List.of();
        }
        return embedded.stream()
            .map(e -> new Suggestion(SuggestionId.of(e.id()), e.text(), e.reason(), e.confidence()))
            .toList();
    }
}
