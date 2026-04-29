package app.backend.jamo.diary.infrastructure.persistence;

import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedback;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedbackId;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceText;
import app.backend.jamo.diary.domain.model.sentencefeedback.Suggestion;
import app.backend.jamo.diary.domain.model.sentencefeedback.SuggestionId;
import app.backend.jamo.diary.domain.repository.SentenceFeedbackRepository;
import app.backend.jamo.diary.infrastructure.persistence.entity.OutboxEventJpaEntity;
import app.backend.jamo.diary.infrastructure.persistence.entity.ProcessedEventJpaEntity;
import app.backend.jamo.diary.infrastructure.persistence.repository.SentenceFeedbackRepositoryImpl;
import app.backend.jamo.diary.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import app.backend.jamo.diary.infrastructure.persistence.repository.SpringDataProcessedEventRepository;
import app.backend.jamo.diary.infrastructure.persistence.repository.SpringDataSentenceFeedbackRepository;
import app.backend.jamo.diary.infrastructure.support.AbstractMySQLContainerTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-a-5-impl-batch 의 native query 2종 (findExpirableSuggestedIdsForUpdate / findFinalOlderThanIdsForUpdate)
 * + deleteByIdIn 정합 검증. Testcontainer MySQL 8 (SKIP LOCKED 지원).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SentenceFeedbackRepositoryImpl.class)
@ActiveProfiles("test")
class SentenceFeedbackBatchRepositoryDataJpaTest extends AbstractMySQLContainerTest {

    @Autowired private SentenceFeedbackRepository repository;
    @Autowired private SpringDataSentenceFeedbackRepository jpa;
    @Autowired private SpringDataOutboxEventRepository outboxRepository;
    @Autowired private SpringDataProcessedEventRepository processedRepository;
    @Autowired private EntityManager entityManager;

    private final Clock clock = Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void findExpirableSuggestedBefore_returns_only_SUGGESTED_with_expiresAt_before_cutoff() {
        // 1) SUGGESTED + 만료됨 (cutoff 전) — 후보
        SentenceFeedbackId expiredCandidate = makeSuggested(Instant.parse("2026-04-29T05:00:00Z"));
        // 2) SUGGESTED + 만료 안됨 (cutoff 후) — 제외
        SentenceFeedbackId stillValid = makeSuggested(Instant.parse("2026-04-29T15:00:00Z"));
        // 3) REQUESTED — 제외
        SentenceFeedbackId requested = makeRequested();
        // 4) ACCEPTED — 제외
        SentenceFeedbackId accepted = makeAccepted();
        flushClear();

        Instant cutoff = Instant.parse("2026-04-29T10:00:00Z");
        List<SentenceFeedbackId> ids = repository.findExpirableSuggestedBefore(cutoff, 100);

        assertThat(ids).hasSize(1);
        assertThat(ids.get(0).value()).isEqualTo(expiredCandidate.value());
    }

    @Test
    void findExpirableSuggestedBefore_respects_chunk_limit() {
        for (int i = 0; i < 5; i++) {
            makeSuggested(Instant.parse("2026-04-29T05:00:00Z").minusSeconds(i));
        }
        flushClear();

        List<SentenceFeedbackId> ids = repository.findExpirableSuggestedBefore(
            Instant.parse("2026-04-29T10:00:00Z"), 3);

        assertThat(ids).hasSize(3);
    }

    @Test
    void findFinalOlderThan_excludes_recently_decided_rows_after_cutoff() {
        // test-reviewer H3 — cutoff 부등호 검증. 모든 final fixture 가 cutoff 이전이면 부등호 회귀 못 잡음.
        SentenceFeedbackId recentAccepted = makeAcceptedAt(Instant.parse("2026-03-29T10:00:00Z"));
        SentenceFeedbackId oldAccepted = makeAccepted();
        flushClear();

        Instant cutoff = Instant.parse("2026-04-29T10:00:00Z").minus(Duration.ofDays(90));
        List<SentenceFeedbackId> ids = repository.findFinalOlderThan(cutoff, 100);

        assertThat(ids).extracting(SentenceFeedbackId::value)
            .contains(oldAccepted.value())
            .doesNotContain(recentAccepted.value());
    }

    @Test
    void findFinalOlderThan_returns_only_final_status_decided_before_cutoff() {
        // ACCEPTED + 1년 전 — 후보
        SentenceFeedbackId oldAccepted = makeAccepted();
        // REJECTED + 100일 전 — 후보
        SentenceFeedbackId oldRejected = makeRejected();
        // EXPIRED + 100일 전 — 후보
        SentenceFeedbackId oldExpired = makeExpired();
        // FAILED + 100일 전 — 후보
        SentenceFeedbackId oldFailed = makeFailed();
        // SUGGESTED — 제외 (final 아님)
        SentenceFeedbackId stillSuggested = makeSuggested(Instant.parse("2026-04-29T15:00:00Z"));
        flushClear();

        Instant cutoff = Instant.parse("2026-04-29T10:00:00Z").minus(Duration.ofDays(90));
        List<SentenceFeedbackId> ids = repository.findFinalOlderThan(cutoff, 100);

        // 모든 4 final row 가 90일 이전 (test fixture 가 충분히 과거 시각으로 생성)
        assertThat(ids).extracting(SentenceFeedbackId::value)
            .containsExactlyInAnyOrder(
                oldAccepted.value(),
                oldRejected.value(),
                oldExpired.value(),
                oldFailed.value()
            );
        assertThat(ids).extracting(SentenceFeedbackId::value)
            .doesNotContain(stillSuggested.value());
    }

    @Test
    void deleteByIds_removes_only_given_ids() {
        SentenceFeedbackId keep = makeAccepted();
        SentenceFeedbackId removeA = makeRejected();
        SentenceFeedbackId removeB = makeFailed();
        flushClear();

        int deleted = repository.deleteByIds(List.of(removeA, removeB));
        flushClear();

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findById(keep)).isPresent();
        assertThat(repository.findById(removeA)).isEmpty();
        assertThat(repository.findById(removeB)).isEmpty();
    }

    @Test
    void deleteByIds_empty_list_no_op() {
        // service 가 empty list 받으면 0 반환 + JPQL 호출 안 함 — 본 검증은 RepositoryImpl 의 분기.
        int deleted = repository.deleteByIds(List.of());

        assertThat(deleted).isZero();
    }

    // ============================================================
    // test-reviewer H2 — Outbox / ProcessedEvent retention 쿼리 검증
    // ============================================================

    @Test
    void deletePublishedBefore_only_affects_published_rows_before_cutoff() {
        // 7일 이전 published — 후보
        OutboxEventJpaEntity oldPublished = new OutboxEventJpaEntity(
            UUID.randomUUID().toString(), "sentence_feedback", "agg-1", "Type1",
            "diary-events", "{\"a\":1}", Instant.parse("2026-04-15T00:00:00Z")
        );
        oldPublished.markPublished(Instant.parse("2026-04-15T00:00:01Z"));  // 14일 전
        outboxRepository.save(oldPublished);
        // 1일 전 published — 보존
        OutboxEventJpaEntity recentPublished = new OutboxEventJpaEntity(
            UUID.randomUUID().toString(), "sentence_feedback", "agg-2", "Type1",
            "diary-events", "{\"a\":2}", Instant.parse("2026-04-28T10:00:00Z")
        );
        recentPublished.markPublished(Instant.parse("2026-04-28T10:00:01Z"));
        outboxRepository.save(recentPublished);
        // 미발행 (`published_at IS NULL`) — 본 cleanup 의 핵심 보존 분기
        OutboxEventJpaEntity unpublished = new OutboxEventJpaEntity(
            UUID.randomUUID().toString(), "sentence_feedback", "agg-3", "Type1",
            "diary-events", "{\"a\":3}", Instant.parse("2026-01-01T00:00:00Z")
        );
        outboxRepository.save(unpublished);
        flushClear();

        Instant cutoff = Instant.parse("2026-04-29T00:00:00Z").minus(Duration.ofDays(7));
        int deleted = outboxRepository.deletePublishedBefore(cutoff);
        flushClear();

        assertThat(deleted).isEqualTo(1);
        assertThat(outboxRepository.findById(oldPublished.getId())).isEmpty();
        assertThat(outboxRepository.findById(recentPublished.getId())).isPresent();
        // 핵심 — published_at IS NULL row 보존 (영구 stuck row 보호)
        assertThat(outboxRepository.findById(unpublished.getId())).isPresent();
    }

    @Test
    void deleteProcessedBefore_removes_only_rows_before_cutoff() {
        ProcessedEventJpaEntity old = new ProcessedEventJpaEntity(
            "diary.X", "evt-old", Instant.parse("2026-03-01T00:00:00Z")  // 30일 이전
        );
        ProcessedEventJpaEntity recent = new ProcessedEventJpaEntity(
            "diary.X", "evt-recent", Instant.parse("2026-04-28T00:00:00Z")
        );
        processedRepository.save(old);
        processedRepository.save(recent);
        flushClear();

        Instant cutoff = Instant.parse("2026-04-29T00:00:00Z").minus(Duration.ofDays(30));
        int deleted = processedRepository.deleteProcessedBefore(cutoff);
        flushClear();

        assertThat(deleted).isEqualTo(1);
        assertThat(processedRepository.findById(old.getId())).isEmpty();
        assertThat(processedRepository.findById(recent.getId())).isPresent();
    }

    // ============================================================
    // Fixture helpers — fixed-clock 의존 회피 (markSuggested invariant)
    // ============================================================

    private SentenceFeedbackId makeRequested() {
        SentenceFeedbackId id = SentenceFeedbackId.newId();
        SentenceFeedback fb = SentenceFeedback.request(
            id, UUID.randomUUID(), null, new SentenceText("hi"), null,
            Instant.parse("2026-01-01T00:00:00Z")
        );
        repository.save(fb);
        return id;
    }

    private SentenceFeedbackId makeSuggested(Instant expiresAt) {
        SentenceFeedbackId id = SentenceFeedbackId.newId();
        SentenceFeedback fb = SentenceFeedback.request(
            id, UUID.randomUUID(), null, new SentenceText("hi"), null,
            Instant.parse("2026-01-01T00:00:00Z")
        );
        Clock pastClock = Clock.fixed(expiresAt.minus(Duration.ofMinutes(1)), ZoneOffset.UTC);
        fb.markSuggested(
            List.of(new Suggestion(SuggestionId.newId(), "alt", "reason", 0.5)),
            expiresAt,
            pastClock
        );
        repository.save(fb);
        return id;
    }

    /** test-reviewer H3 — cutoff 부등호 검증용 — decided_at 외부 주입. */
    private SentenceFeedbackId makeAcceptedAt(Instant decidedAt) {
        SentenceFeedbackId id = SentenceFeedbackId.newId();
        SentenceFeedback fb = SentenceFeedback.request(
            id, UUID.randomUUID(), null, new SentenceText("hi"), null,
            decidedAt.minus(Duration.ofMinutes(2))
        );
        Instant exp = decidedAt.plus(Duration.ofHours(1));
        Clock pastClock = Clock.fixed(decidedAt.minus(Duration.ofMinutes(1)), ZoneOffset.UTC);
        SuggestionId sid = SuggestionId.newId();
        fb.markSuggested(
            List.of(new Suggestion(sid, "alt", "reason", 0.5)),
            exp,
            pastClock
        );
        Clock decidedClock = Clock.fixed(decidedAt, ZoneOffset.UTC);
        fb.accept(sid, decidedClock);
        repository.save(fb);
        return id;
    }

    private SentenceFeedbackId makeAccepted() {
        SentenceFeedbackId id = SentenceFeedbackId.newId();
        SentenceFeedback fb = SentenceFeedback.request(
            id, UUID.randomUUID(), null, new SentenceText("hi"), null,
            Instant.parse("2026-01-01T00:00:00Z")
        );
        Instant exp = Instant.parse("2026-01-01T01:00:00Z");
        Clock pastClock = Clock.fixed(Instant.parse("2026-01-01T00:00:30Z"), ZoneOffset.UTC);
        SuggestionId sid = SuggestionId.newId();
        fb.markSuggested(
            List.of(new Suggestion(sid, "alt", "reason", 0.5)),
            exp,
            pastClock
        );
        fb.accept(sid, pastClock);
        repository.save(fb);
        return id;
    }

    private SentenceFeedbackId makeRejected() {
        SentenceFeedbackId id = SentenceFeedbackId.newId();
        SentenceFeedback fb = SentenceFeedback.request(
            id, UUID.randomUUID(), null, new SentenceText("hi"), null,
            Instant.parse("2026-01-01T00:00:00Z")
        );
        Instant exp = Instant.parse("2026-01-01T01:00:00Z");
        Clock pastClock = Clock.fixed(Instant.parse("2026-01-01T00:00:30Z"), ZoneOffset.UTC);
        fb.markSuggested(
            List.of(new Suggestion(SuggestionId.newId(), "alt", "reason", 0.5)),
            exp,
            pastClock
        );
        fb.reject(null, pastClock);
        repository.save(fb);
        return id;
    }

    private SentenceFeedbackId makeExpired() {
        SentenceFeedbackId id = SentenceFeedbackId.newId();
        SentenceFeedback fb = SentenceFeedback.request(
            id, UUID.randomUUID(), null, new SentenceText("hi"), null,
            Instant.parse("2026-01-01T00:00:00Z")
        );
        Instant exp = Instant.parse("2026-01-01T01:00:00Z");
        Clock pastClock = Clock.fixed(Instant.parse("2026-01-01T00:00:30Z"), ZoneOffset.UTC);
        fb.markSuggested(
            List.of(new Suggestion(SuggestionId.newId(), "alt", "reason", 0.5)),
            exp,
            pastClock
        );
        Clock afterExp = Clock.fixed(Instant.parse("2026-01-01T02:00:00Z"), ZoneOffset.UTC);
        fb.expire(afterExp);
        repository.save(fb);
        return id;
    }

    private SentenceFeedbackId makeFailed() {
        SentenceFeedbackId id = SentenceFeedbackId.newId();
        SentenceFeedback fb = SentenceFeedback.request(
            id, UUID.randomUUID(), null, new SentenceText("hi"), null,
            Instant.parse("2026-01-01T00:00:00Z")
        );
        Clock c = Clock.fixed(Instant.parse("2026-01-01T00:00:30Z"), ZoneOffset.UTC);
        fb.markFailed("ai gateway timeout", c);
        repository.save(fb);
        return id;
    }

    private void flushClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
