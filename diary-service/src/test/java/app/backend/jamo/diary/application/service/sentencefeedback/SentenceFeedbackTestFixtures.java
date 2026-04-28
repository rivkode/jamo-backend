package app.backend.jamo.diary.application.service.sentencefeedback;

import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedback;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedbackId;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceText;
import app.backend.jamo.diary.domain.model.sentencefeedback.Suggestion;
import app.backend.jamo.diary.domain.model.sentencefeedback.SuggestionId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Application Service 테스트 공유 fixture — Accept/Reject 테스트의 동일 helper 중복 제거.
 */
final class SentenceFeedbackTestFixtures {

    private SentenceFeedbackTestFixtures() {
    }

    /** SUGGESTED 상태의 SentenceFeedback (suggestion 1개) — 24h TTL. */
    static SentenceFeedback suggested(UUID userId, Instant now) {
        SentenceFeedback fb = SentenceFeedback.request(
            SentenceFeedbackId.newId(), userId, null,
            new SentenceText("테스트 문장"), null, now
        );
        Suggestion s = new Suggestion(SuggestionId.newId(), "더 자연스러운", "맥락", 0.9);
        fb.markSuggested(List.of(s), now.plus(Duration.ofHours(24)), Clock.fixed(now, ZoneOffset.UTC));
        return fb;
    }
}
