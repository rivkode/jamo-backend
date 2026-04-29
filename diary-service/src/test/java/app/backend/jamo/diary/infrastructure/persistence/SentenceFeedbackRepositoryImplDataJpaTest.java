package app.backend.jamo.diary.infrastructure.persistence;

import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedback;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedbackId;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceText;
import app.backend.jamo.diary.domain.model.sentencefeedback.Status;
import app.backend.jamo.diary.domain.model.sentencefeedback.Suggestion;
import app.backend.jamo.diary.domain.model.sentencefeedback.SuggestionId;
import app.backend.jamo.diary.domain.model.sentencefeedback.Tone;
import app.backend.jamo.diary.infrastructure.persistence.repository.SentenceFeedbackRepositoryImpl;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SentenceFeedbackRepositoryImpl @DataJpaTest — Testcontainers MySQL 8 (JSON 컬럼 + Flyway V5).
 *
 * <p>검증:
 * <ul>
 *   <li>save → findById round-trip (모든 필드 + tone null + diaryId null 분기)</li>
 *   <li>save 두 번 (UPSERT, mergeInto) — 상태 전이 후 결과 영속</li>
 *   <li>findByIdAndUserId IDOR — 다른 userId 면 empty</li>
 *   <li>deleteAllByDiaryId — diaryId 일치 row 만 삭제 + NULL row 무관</li>
 *   <li>deleteAllByUserId — userId 일치 모든 row (final 상태 무관) 삭제</li>
 *   <li>suggestions JSON round-trip — UUID / 한글 / 이모지 보존</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SentenceFeedbackRepositoryImpl.class)
@ActiveProfiles("test")
class SentenceFeedbackRepositoryImplDataJpaTest extends AbstractMySQLContainerTest {

    @Autowired private SentenceFeedbackRepositoryImpl repository;
    @Autowired private SpringDataSentenceFeedbackRepository jpa;
    @Autowired private EntityManager entityManager;

    private final Clock clock = Clock.systemUTC();

    @Test
    void save_then_findById_round_trip_REQUESTED() {
        UUID userId = UUID.randomUUID();
        UUID diaryId = UUID.randomUUID();
        SentenceFeedbackId id = SentenceFeedbackId.newId();
        SentenceFeedback fb = SentenceFeedback.request(
            id, userId, diaryId,
            new SentenceText("오늘은 즐거운 하루 🌞"), Tone.CASUAL, Instant.now(clock)
        );

        repository.save(fb);
        flushAndClear();

        Optional<SentenceFeedback> loaded = repository.findById(id);

        assertThat(loaded).isPresent();
        SentenceFeedback got = loaded.get();
        assertThat(got.id()).isEqualTo(id);
        assertThat(got.userId()).isEqualTo(userId);
        assertThat(got.diaryId()).contains(diaryId);
        assertThat(got.originalSentence().value()).isEqualTo("오늘은 즐거운 하루 🌞");
        assertThat(got.tone()).contains(Tone.CASUAL);
        assertThat(got.status()).isEqualTo(Status.REQUESTED);
        assertThat(got.suggestions()).isEmpty();
        assertThat(got.expiresAt()).isEmpty();
        assertThat(got.decisionSuggestionId()).isEmpty();
    }

    @Test
    void save_round_trip_with_null_diary_and_null_tone() {
        SentenceFeedbackId id = SentenceFeedbackId.newId();
        SentenceFeedback fb = SentenceFeedback.request(
            id, UUID.randomUUID(), null,  // diaryId null (작성 전 미리보기 §5)
            new SentenceText("preview"), null,  // tone null (사용자 미명시 §10)
            Instant.now(clock)
        );

        repository.save(fb);
        flushAndClear();

        SentenceFeedback got = repository.findById(id).orElseThrow();
        assertThat(got.diaryId()).isEmpty();
        assertThat(got.tone()).isEmpty();
    }

    @Test
    void save_then_markSuggested_then_save_persists_SUGGESTED_with_JSON_suggestions() {
        SentenceFeedbackId id = SentenceFeedbackId.newId();
        SentenceFeedback fb = SentenceFeedback.request(
            id, UUID.randomUUID(), UUID.randomUUID(),
            new SentenceText("배고프다"), Tone.NEUTRAL, Instant.now(clock)
        );
        repository.save(fb);
        flushAndClear();

        SentenceFeedback loaded = repository.findById(id).orElseThrow();
        Instant expiresAt = Instant.now(clock).plus(Duration.ofHours(24));
        loaded.markSuggested(List.of(
            new Suggestion(SuggestionId.newId(), "배가 고프네요", "정중한 표현", 0.92),
            new Suggestion(SuggestionId.newId(), "허기진다 😋", "이모지 포함", 0.71)
        ), expiresAt, clock);
        repository.save(loaded);
        flushAndClear();

        SentenceFeedback after = repository.findById(id).orElseThrow();
        assertThat(after.status()).isEqualTo(Status.SUGGESTED);
        assertThat(after.suggestions()).hasSize(2);
        assertThat(after.suggestions().get(0).text()).isEqualTo("배가 고프네요");
        assertThat(after.suggestions().get(0).reason()).isEqualTo("정중한 표현");
        assertThat(after.suggestions().get(0).confidence()).isEqualTo(0.92);
        assertThat(after.suggestions().get(1).text()).isEqualTo("허기진다 😋");
        assertThat(after.expiresAt()).isPresent();
    }

    @Test
    void findByIdAndUserId_returns_empty_for_other_user_IDOR() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        SentenceFeedbackId id = SentenceFeedbackId.newId();
        repository.save(SentenceFeedback.request(
            id, owner, null, new SentenceText("hello"), null, Instant.now(clock)
        ));
        flushAndClear();

        assertThat(repository.findByIdAndUserId(id, owner)).isPresent();
        assertThat(repository.findByIdAndUserId(id, other)).isEmpty();
    }

    @Test
    void deleteAllByDiaryId_only_affects_matching_diary_rows() {
        UUID userId = UUID.randomUUID();
        UUID diaryA = UUID.randomUUID();
        UUID diaryB = UUID.randomUUID();

        SentenceFeedbackId rowA1 = SentenceFeedbackId.newId();
        SentenceFeedbackId rowA2 = SentenceFeedbackId.newId();
        SentenceFeedbackId rowB = SentenceFeedbackId.newId();
        SentenceFeedbackId rowNullDiary = SentenceFeedbackId.newId();

        repository.save(SentenceFeedback.request(rowA1, userId, diaryA, new SentenceText("a1"), null, Instant.now(clock)));
        repository.save(SentenceFeedback.request(rowA2, userId, diaryA, new SentenceText("a2"), null, Instant.now(clock)));
        repository.save(SentenceFeedback.request(rowB, userId, diaryB, new SentenceText("b"), null, Instant.now(clock)));
        repository.save(SentenceFeedback.request(rowNullDiary, userId, null, new SentenceText("preview"), null, Instant.now(clock)));
        flushAndClear();

        int deleted = repository.deleteAllByDiaryId(diaryA);
        flushAndClear();

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findById(rowA1)).isEmpty();
        assertThat(repository.findById(rowA2)).isEmpty();
        assertThat(repository.findById(rowB)).isPresent();
        assertThat(repository.findById(rowNullDiary)).isPresent();  // §5 — NULL diaryId 영향 X
    }

    @Test
    void deleteAllByUserId_removes_all_rows_for_user_regardless_of_status() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        SentenceFeedbackId rowA1 = SentenceFeedbackId.newId();
        SentenceFeedbackId rowA2 = SentenceFeedbackId.newId();
        SentenceFeedbackId rowB = SentenceFeedbackId.newId();

        repository.save(SentenceFeedback.request(rowA1, userA, UUID.randomUUID(), new SentenceText("a1"), null, Instant.now(clock)));
        SentenceFeedback fbA2 = SentenceFeedback.request(rowA2, userA, null, new SentenceText("a2"), null, Instant.now(clock));
        fbA2.markFailed("test", clock);
        repository.save(fbA2);
        repository.save(SentenceFeedback.request(rowB, userB, UUID.randomUUID(), new SentenceText("b"), null, Instant.now(clock)));
        flushAndClear();

        int deleted = repository.deleteAllByUserId(userA);
        flushAndClear();

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findById(rowA1)).isEmpty();
        assertThat(repository.findById(rowA2)).isEmpty();
        assertThat(repository.findById(rowB)).isPresent();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
