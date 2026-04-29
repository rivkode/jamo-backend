package app.backend.jamo.diary.infrastructure.persistence;

import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.model.diarylike.DiaryLike;
import app.backend.jamo.diary.infrastructure.persistence.repository.DiaryLikeRepositoryImpl;
import app.backend.jamo.diary.infrastructure.support.AbstractMySQLContainerTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DiaryLikeRepositoryImpl @DataJpaTest — UNIQUE 제약 + 일괄 likedByMe + cascade.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DiaryLikeRepositoryImpl.class)
@ActiveProfiles("test")
class DiaryLikeRepositoryImplDataJpaTest extends AbstractMySQLContainerTest {

    @Autowired private DiaryLikeRepositoryImpl repository;
    @Autowired private EntityManager entityManager;

    private final Instant baseTime = Instant.parse("2026-04-30T10:00:00Z");
    private final Clock clock = Clock.fixed(baseTime, ZoneOffset.UTC);

    @Test
    void save_then_findByDiaryIdAndUserId_round_trip() {
        DiaryId diaryId = DiaryId.newId();
        UUID user = UUID.randomUUID();
        DiaryLike like = DiaryLike.create(diaryId, user, clock);

        repository.save(like);
        flushAndClear();

        assertThat(repository.findByDiaryIdAndUserId(diaryId, user)).isPresent();
        assertThat(repository.existsByDiaryIdAndUserId(diaryId, user)).isTrue();
    }

    @Test
    void unique_constraint_blocks_duplicate_diary_user_pair() {
        // 박제 §8 — drift 방지 최후 보루. UNIQUE (diary_id, user_id) 위반 시 RuntimeException
        // (Hibernate ConstraintViolationException / Spring DataIntegrityViolationException 계열).
        DiaryId diaryId = DiaryId.newId();
        UUID user = UUID.randomUUID();
        repository.save(DiaryLike.create(diaryId, user, clock));
        flushAndClear();

        DiaryLike duplicate = DiaryLike.create(diaryId, user, clock);
        repository.save(duplicate);
        // entityManager.flush() 직접 호출이라 Spring `@Repository` AOP translate 미적용 — Hibernate
        // ConstraintViolationException 그대로 전파될 수 있음. 두 타입 모두 허용 (Application Service 가
        // catch 하는 시점에는 Spring AOP 가 DataIntegrityViolationException 으로 wrap).
        assertThatThrownBy(this::flushAndClear).isInstanceOfAny(
            DataIntegrityViolationException.class,
            ConstraintViolationException.class);
    }

    @Test
    void deleteByDiaryIdAndUserId_is_idempotent() {
        DiaryId diaryId = DiaryId.newId();
        UUID user = UUID.randomUUID();

        // not exist → no-op
        repository.deleteByDiaryIdAndUserId(diaryId, user);
        flushAndClear();
        assertThat(repository.existsByDiaryIdAndUserId(diaryId, user)).isFalse();

        // exists → delete
        repository.save(DiaryLike.create(diaryId, user, clock));
        flushAndClear();
        repository.deleteByDiaryIdAndUserId(diaryId, user);
        flushAndClear();
        assertThat(repository.existsByDiaryIdAndUserId(diaryId, user)).isFalse();
    }

    @Test
    void countByDiaryId_matches() {
        DiaryId diaryId = DiaryId.newId();
        repository.save(DiaryLike.create(diaryId, UUID.randomUUID(), clock));
        repository.save(DiaryLike.create(diaryId, UUID.randomUUID(), clock));
        repository.save(DiaryLike.create(diaryId, UUID.randomUUID(), clock));
        flushAndClear();

        assertThat(repository.countByDiaryId(diaryId)).isEqualTo(3);
    }

    @Test
    void findDiaryIdsLikedByUser_returns_subset() {
        UUID user = UUID.randomUUID();
        DiaryId d1 = DiaryId.newId();
        DiaryId d2 = DiaryId.newId();
        DiaryId d3 = DiaryId.newId();
        repository.save(DiaryLike.create(d1, user, clock));
        repository.save(DiaryLike.create(d3, user, clock));
        flushAndClear();

        Set<DiaryId> liked = repository.findDiaryIdsLikedByUser(user, Set.of(d1, d2, d3));

        assertThat(liked).containsExactlyInAnyOrder(d1, d3);
    }

    @Test
    void findDiaryIdsLikedByUser_empty_input_returns_empty() {
        UUID user = UUID.randomUUID();
        Set<DiaryId> liked = repository.findDiaryIdsLikedByUser(user, Set.of());
        assertThat(liked).isEmpty();
    }

    @Test
    void deleteAllByDiaryId_cascade() {
        DiaryId target = DiaryId.newId();
        DiaryId other = DiaryId.newId();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        repository.save(DiaryLike.create(target, userA, clock));
        repository.save(DiaryLike.create(target, userB, clock));
        repository.save(DiaryLike.create(other, userA, clock));
        flushAndClear();

        int deleted = repository.deleteAllByDiaryId(target);
        flushAndClear();

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.countByDiaryId(target)).isZero();
        assertThat(repository.countByDiaryId(other)).isEqualTo(1);
    }

    @Test
    void deleteAllByUserId_cascade() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        DiaryId d1 = DiaryId.newId();
        DiaryId d2 = DiaryId.newId();
        repository.save(DiaryLike.create(d1, userA, clock));
        repository.save(DiaryLike.create(d2, userA, clock));
        repository.save(DiaryLike.create(d1, userB, clock));
        flushAndClear();

        int deleted = repository.deleteAllByUserId(userA);
        flushAndClear();

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findDiaryIdsLikedByUser(userA, Set.of(d1, d2))).isEmpty();
        assertThat(repository.findDiaryIdsLikedByUser(userB, Set.of(d1))).contains(d1);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
