package app.backend.jamo.diary.domain.model.sentencefeedback;

import app.backend.jamo.diary.domain.exception.SentenceFeedbackInvalidTransitionException;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackUnknownSuggestionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentenceFeedbackTest {

    private SentenceFeedbackId feedbackId;
    private UUID userId;
    private UUID diaryId;
    private SentenceText sentence;
    private Instant now;
    private Suggestion s1;
    private Suggestion s2;

    @BeforeEach
    void setUp() {
        feedbackId = SentenceFeedbackId.newId();
        userId = UUID.randomUUID();
        diaryId = UUID.randomUUID();
        sentence = new SentenceText("오늘 날씨 좋아요");
        now = Instant.parse("2026-04-28T10:00:00Z");
        s1 = new Suggestion(SuggestionId.newId(), "오늘 날씨가 참 좋네요", "더 자연스러운 표현", 0.92);
        s2 = new Suggestion(SuggestionId.newId(), "오늘 정말 좋은 날씨예요", "감정 강조", 0.85);
    }

    private Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    // ============================================================
    // Factories
    // ============================================================

    @Nested
    class Request {

        @Test
        void creates_REQUESTED_with_all_fields() {
            SentenceFeedback fb = SentenceFeedback.request(
                feedbackId, userId, diaryId, sentence, Tone.CASUAL, now);
            assertAll(
                () -> assertEquals(feedbackId, fb.id()),
                () -> assertEquals(userId, fb.userId()),
                () -> assertEquals(Optional.of(diaryId), fb.diaryId()),
                () -> assertEquals(sentence, fb.originalSentence()),
                () -> assertEquals(Optional.of(Tone.CASUAL), fb.tone()),
                () -> assertEquals(Status.REQUESTED, fb.status()),
                () -> assertTrue(fb.suggestions().isEmpty()),
                () -> assertEquals(now, fb.createdAt())
            );
        }

        @Test
        void allows_null_diaryId_for_preview_flow() {
            // §5 — 작성 전 미리보기 흐름
            SentenceFeedback fb = SentenceFeedback.request(
                feedbackId, userId, null, sentence, null, now);
            assertEquals(Optional.empty(), fb.diaryId());
            assertEquals(Optional.empty(), fb.tone());
        }

        @Test
        void rejects_null_userId() {
            assertThrows(NullPointerException.class,
                () -> SentenceFeedback.request(feedbackId, null, diaryId, sentence, null, now));
        }
    }

    // ============================================================
    // markSuggested — REQUESTED → SUGGESTED
    // ============================================================

    @Nested
    class MarkSuggested {

        @Test
        void transitions_REQUESTED_to_SUGGESTED() {
            SentenceFeedback fb = SentenceFeedback.request(feedbackId, userId, diaryId, sentence, null, now);
            Instant expiresAt = now.plus(Duration.ofHours(24));
            fb.markSuggested(List.of(s1, s2), expiresAt, fixedClock(now));
            assertAll(
                () -> assertEquals(Status.SUGGESTED, fb.status()),
                () -> assertEquals(List.of(s1, s2), fb.suggestions()),
                () -> assertEquals(Optional.of(expiresAt), fb.expiresAt())
            );
        }

        @Test
        void rejects_empty_suggestions() {
            // §7 — markFailed 와 의미 분리
            SentenceFeedback fb = SentenceFeedback.request(feedbackId, userId, diaryId, sentence, null, now);
            assertThrows(IllegalArgumentException.class,
                () -> fb.markSuggested(List.of(), now.plus(Duration.ofHours(24)), fixedClock(now)));
        }

        @Test
        void rejects_expiresAt_not_after_now() {
            SentenceFeedback fb = SentenceFeedback.request(feedbackId, userId, diaryId, sentence, null, now);
            assertThrows(IllegalArgumentException.class,
                () -> fb.markSuggested(List.of(s1), now, fixedClock(now)));
        }

        @Test
        void rejects_call_in_SUGGESTED_state() {
            SentenceFeedback fb = suggestedFeedback();
            assertThrows(SentenceFeedbackInvalidTransitionException.class,
                () -> fb.markSuggested(List.of(s1), now.plus(Duration.ofHours(24)), fixedClock(now)));
        }

        @Test
        void rejects_call_in_FAILED_state() {
            SentenceFeedback fb = SentenceFeedback.request(feedbackId, userId, diaryId, sentence, null, now);
            fb.markFailed("ai timeout", fixedClock(now));
            assertThrows(SentenceFeedbackInvalidTransitionException.class,
                () -> fb.markSuggested(List.of(s1), now.plus(Duration.ofHours(24)), fixedClock(now)));
        }

        @Test
        void rejects_call_in_ACCEPTED_state() {
            SentenceFeedback fb = suggestedFeedback();
            fb.accept(s1.id(), fixedClock(now));
            assertThrows(SentenceFeedbackInvalidTransitionException.class,
                () -> fb.markSuggested(List.of(s2), now.plus(Duration.ofHours(48)), fixedClock(now)));
        }

        @Test
        void rejects_call_in_REJECTED_state() {
            SentenceFeedback fb = suggestedFeedback();
            fb.reject(null, fixedClock(now));
            assertThrows(SentenceFeedbackInvalidTransitionException.class,
                () -> fb.markSuggested(List.of(s2), now.plus(Duration.ofHours(48)), fixedClock(now)));
        }

        @Test
        void rejects_call_in_EXPIRED_state() {
            SentenceFeedback fb = suggestedFeedback();
            fb.expire(fixedClock(now.plus(Duration.ofHours(25))));
            assertThrows(SentenceFeedbackInvalidTransitionException.class,
                () -> fb.markSuggested(List.of(s2), now.plus(Duration.ofHours(48)), fixedClock(now.plus(Duration.ofHours(25)))));
        }

        @Test
        void suggestions_returned_as_immutable_copy() {
            SentenceFeedback fb = SentenceFeedback.request(feedbackId, userId, diaryId, sentence, null, now);
            fb.markSuggested(List.of(s1, s2), now.plus(Duration.ofHours(24)), fixedClock(now));
            List<Suggestion> returned = fb.suggestions();
            assertThrows(UnsupportedOperationException.class, () -> returned.add(s1));
        }
    }

    // ============================================================
    // accept — SUGGESTED → ACCEPTED
    // ============================================================

    @Nested
    class Accept {

        @Test
        void transitions_SUGGESTED_to_ACCEPTED_with_decided_at() {
            SentenceFeedback fb = suggestedFeedback();
            Instant decidedTime = now.plus(Duration.ofMinutes(5));
            fb.accept(s1.id(), fixedClock(decidedTime));
            assertAll(
                () -> assertEquals(Status.ACCEPTED, fb.status()),
                () -> assertEquals(Optional.of(s1.id()), fb.decisionSuggestionId()),
                () -> assertEquals(Optional.of(decidedTime), fb.decidedAt())
            );
        }

        @Test
        void rejects_unknown_suggestionId_with_400() {
            // §4 — 400 Bad Request (state transition 정상 + 입력 데이터 잘못)
            SentenceFeedback fb = suggestedFeedback();
            SuggestionId unknown = SuggestionId.newId();
            assertThrows(SentenceFeedbackUnknownSuggestionException.class,
                () -> fb.accept(unknown, fixedClock(now)));
        }

        @Test
        void rejects_call_in_REQUESTED_state() {
            SentenceFeedback fb = SentenceFeedback.request(feedbackId, userId, diaryId, sentence, null, now);
            assertThrows(SentenceFeedbackInvalidTransitionException.class,
                () -> fb.accept(s1.id(), fixedClock(now)));
        }

        @Test
        void rejects_call_in_ACCEPTED_state_double_accept() {
            // final 상태 전이 차단
            SentenceFeedback fb = suggestedFeedback();
            fb.accept(s1.id(), fixedClock(now));
            assertThrows(SentenceFeedbackInvalidTransitionException.class,
                () -> fb.accept(s2.id(), fixedClock(now)));
        }

        @Test
        void rejects_call_in_REJECTED_state() {
            SentenceFeedback fb = suggestedFeedback();
            fb.reject("not useful", fixedClock(now));
            assertThrows(SentenceFeedbackInvalidTransitionException.class,
                () -> fb.accept(s1.id(), fixedClock(now)));
        }
    }

    // ============================================================
    // reject — SUGGESTED → REJECTED
    // ============================================================

    @Nested
    class Reject {

        @Test
        void transitions_SUGGESTED_to_REJECTED_with_reason() {
            SentenceFeedback fb = suggestedFeedback();
            Instant decidedTime = now.plus(Duration.ofMinutes(3));
            fb.reject("표현이 어색함", fixedClock(decidedTime));
            assertAll(
                () -> assertEquals(Status.REJECTED, fb.status()),
                () -> assertEquals(Optional.of("표현이 어색함"), fb.rejectionReason()),
                () -> assertEquals(Optional.of(decidedTime), fb.decidedAt())
            );
        }

        @Test
        void allows_null_reason() {
            // §15 자유 텍스트 — 사용자가 안 적을 수 있음
            SentenceFeedback fb = suggestedFeedback();
            fb.reject(null, fixedClock(now));
            assertEquals(Optional.empty(), fb.rejectionReason());
        }

        @Test
        void normalizes_blank_reason_to_null() {
            SentenceFeedback fb = suggestedFeedback();
            fb.reject("   ", fixedClock(now));
            assertEquals(Optional.empty(), fb.rejectionReason());
        }

        @Test
        void rejects_reason_exceeding_1000_code_points() {
            // ddd-architect Q4 NEEDS CHANGES — 1000 cp 상한
            SentenceFeedback fb = suggestedFeedback();
            String over1000 = "a".repeat(1001);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fb.reject(over1000, fixedClock(now)));
            assertTrue(ex.getMessage().contains("1000"));
        }

        @Test
        void rejects_call_in_REQUESTED_state() {
            SentenceFeedback fb = SentenceFeedback.request(feedbackId, userId, diaryId, sentence, null, now);
            assertThrows(SentenceFeedbackInvalidTransitionException.class,
                () -> fb.reject("any", fixedClock(now)));
        }

        @Test
        void rejects_call_in_REJECTED_state_double_reject() {
            SentenceFeedback fb = suggestedFeedback();
            fb.reject("first", fixedClock(now));
            assertThrows(SentenceFeedbackInvalidTransitionException.class,
                () -> fb.reject("second", fixedClock(now)));
        }
    }

    // ============================================================
    // expire — SUGGESTED → EXPIRED
    // ============================================================

    @Nested
    class Expire {

        @Test
        void transitions_SUGGESTED_to_EXPIRED_after_TTL() {
            SentenceFeedback fb = suggestedFeedback();
            // suggestedFeedback() 의 expiresAt 은 now + 24h
            Instant after24h = now.plus(Duration.ofHours(24)).plusSeconds(1);
            fb.expire(fixedClock(after24h));
            assertAll(
                () -> assertEquals(Status.EXPIRED, fb.status()),
                () -> assertEquals(Optional.of(after24h), fb.decidedAt())
            );
        }

        @Test
        void allows_expire_at_exact_expiresAt() {
            SentenceFeedback fb = suggestedFeedback();
            Instant exactExpiresAt = now.plus(Duration.ofHours(24));
            // clock = expiresAt → not before → 허용
            fb.expire(fixedClock(exactExpiresAt));
            assertEquals(Status.EXPIRED, fb.status());
        }

        @Test
        void rejects_expire_before_TTL_with_invalid_transition() {
            // ddd-architect Q8 NEEDS CHANGES — 예외, no-op X
            SentenceFeedback fb = suggestedFeedback();
            Instant beforeTTL = now.plus(Duration.ofHours(1));
            assertThrows(SentenceFeedbackInvalidTransitionException.class,
                () -> fb.expire(fixedClock(beforeTTL)));
        }

        @Test
        void rejects_call_in_REQUESTED_state() {
            SentenceFeedback fb = SentenceFeedback.request(feedbackId, userId, diaryId, sentence, null, now);
            assertThrows(SentenceFeedbackInvalidTransitionException.class,
                () -> fb.expire(fixedClock(now)));
        }

        @Test
        void rejects_call_in_EXPIRED_state() {
            SentenceFeedback fb = suggestedFeedback();
            fb.expire(fixedClock(now.plus(Duration.ofHours(25))));
            assertThrows(SentenceFeedbackInvalidTransitionException.class,
                () -> fb.expire(fixedClock(now.plus(Duration.ofHours(26)))));
        }
    }

    // ============================================================
    // markFailed — REQUESTED → FAILED
    // ============================================================

    @Nested
    class MarkFailed {

        @Test
        void transitions_REQUESTED_to_FAILED() {
            SentenceFeedback fb = SentenceFeedback.request(feedbackId, userId, diaryId, sentence, null, now);
            Instant decidedTime = now.plusSeconds(35);
            fb.markFailed("ai gateway timeout", fixedClock(decidedTime));
            assertAll(
                () -> assertEquals(Status.FAILED, fb.status()),
                () -> assertEquals(Optional.of("ai gateway timeout"), fb.failureReason()),
                () -> assertEquals(Optional.of(decidedTime), fb.decidedAt())
            );
        }

        @Test
        void rejects_blank_reason() {
            SentenceFeedback fb = SentenceFeedback.request(feedbackId, userId, diaryId, sentence, null, now);
            assertThrows(IllegalArgumentException.class,
                () -> fb.markFailed("   ", fixedClock(now)));
        }

        @Test
        void rejects_null_reason() {
            SentenceFeedback fb = SentenceFeedback.request(feedbackId, userId, diaryId, sentence, null, now);
            assertThrows(IllegalArgumentException.class,
                () -> fb.markFailed(null, fixedClock(now)));
        }

        @Test
        void rejects_reason_exceeding_1000_code_points() {
            SentenceFeedback fb = SentenceFeedback.request(feedbackId, userId, diaryId, sentence, null, now);
            String over1000 = "a".repeat(1001);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fb.markFailed(over1000, fixedClock(now)));
            assertTrue(ex.getMessage().contains("1000"));
        }

        @Test
        void rejects_call_in_SUGGESTED_state() {
            SentenceFeedback fb = suggestedFeedback();
            assertThrows(SentenceFeedbackInvalidTransitionException.class,
                () -> fb.markFailed("any", fixedClock(now)));
        }
    }

    // ============================================================
    // Status helper / equality / reconstitute
    // ============================================================

    @Nested
    class Helpers {

        @Test
        void Status_isFinal_for_terminal_states() {
            assertAll(
                () -> assertFalse(Status.REQUESTED.isFinal()),
                () -> assertFalse(Status.SUGGESTED.isFinal()),
                () -> assertTrue(Status.ACCEPTED.isFinal()),
                () -> assertTrue(Status.REJECTED.isFinal()),
                () -> assertTrue(Status.EXPIRED.isFinal()),
                () -> assertTrue(Status.FAILED.isFinal())
            );
        }

        @Test
        void equals_is_id_based() {
            SentenceFeedback a = SentenceFeedback.request(feedbackId, userId, diaryId, sentence, null, now);
            SentenceFeedback b = SentenceFeedback.request(feedbackId, userId, diaryId,
                new SentenceText("다른 문장"), Tone.FORMAL, now);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void reconstitute_skips_invariant_validation() {
            // DB 데이터 복원용 — 빈 suggestions 도 허용 (REQUESTED 상태 복원 시)
            SentenceFeedback fb = SentenceFeedback.reconstitute(
                feedbackId, userId, null, sentence, null,
                Status.REQUESTED, List.of(), null, null, null, null, null, now
            );
            assertEquals(Status.REQUESTED, fb.status());
            assertTrue(fb.suggestions().isEmpty());
        }
    }

    // ============================================================
    // Test helper
    // ============================================================

    private SentenceFeedback suggestedFeedback() {
        SentenceFeedback fb = SentenceFeedback.request(feedbackId, userId, diaryId, sentence, null, now);
        Instant expiresAt = now.plus(Duration.ofHours(24));
        fb.markSuggested(List.of(s1, s2), expiresAt, fixedClock(now));
        return fb;
    }
}
