package app.backend.jamo.diary.infrastructure.persistence;

import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryContent;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.model.diary.ImageUrls;
import app.backend.jamo.diary.domain.model.diary.Tag;
import app.backend.jamo.diary.domain.model.diary.Tags;
import app.backend.jamo.diary.domain.model.diary.Visibility;
import app.backend.jamo.diary.domain.repository.cursor.PopularFeedCursor;
import app.backend.jamo.diary.domain.repository.cursor.RecentFeedCursor;
import app.backend.jamo.diary.infrastructure.persistence.repository.DiaryRepositoryImpl;
import app.backend.jamo.diary.infrastructure.support.AbstractMySQLContainerTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DiaryRepositoryImpl @DataJpaTest — Testcontainers MySQL 8 + Flyway V7.
 *
 * <p>검증:
 * <ul>
 *   <li>save → findById round-trip (모든 필드, JSON 컬럼, 한글+이모지)</li>
 *   <li>UPSERT (mergeInto) — likeCount 갱신</li>
 *   <li>keyset 페이징 RECENT (no cursor / with cursor) 정확</li>
 *   <li>POPULAR sort tiebreak (like_count, created_at, id)</li>
 *   <li>본인 피드 — visibility 무관</li>
 *   <li>tag 필터 (JSON_CONTAINS)</li>
 *   <li>deleteAllByAuthorId — Saga cleanup</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DiaryRepositoryImpl.class)
@ActiveProfiles("test")
class DiaryRepositoryImplDataJpaTest extends AbstractMySQLContainerTest {

    @Autowired private DiaryRepositoryImpl repository;
    @Autowired private EntityManager entityManager;

    private final Instant baseTime = Instant.parse("2026-04-30T10:00:00Z");

    @Test
    void save_then_findById_round_trip_with_json_collections() {
        UUID author = UUID.randomUUID();
        DiaryId id = DiaryId.newId();
        Diary diary = Diary.create(
            id, author,
            new DiaryContent("오늘 산책 🌞 날씨 좋아"),
            new ImageUrls(List.of("https://cdn.example.com/a.jpg", "https://cdn.example.com/b.png")),
            Tags.ofStrings(List.of("일상", "산책")),
            Visibility.PUBLIC,
            Clock.fixed(baseTime, ZoneOffset.UTC)
        );

        repository.save(diary);
        flushAndClear();

        Optional<Diary> loaded = repository.findById(id);
        assertThat(loaded).isPresent();
        Diary got = loaded.get();
        assertThat(got.id()).isEqualTo(id);
        assertThat(got.authorId()).isEqualTo(author);
        assertThat(got.content().value()).isEqualTo("오늘 산책 🌞 날씨 좋아");
        assertThat(got.images().values()).containsExactly(
            "https://cdn.example.com/a.jpg", "https://cdn.example.com/b.png");
        assertThat(got.tags().asStrings()).containsExactly("일상", "산책");
        assertThat(got.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(got.likeCount()).isZero();
        assertThat(got.createdAt()).isEqualTo(baseTime);
    }

    @Test
    void upsert_persists_like_count_change() {
        UUID author = UUID.randomUUID();
        Diary diary = newPublicDiary(author, "ok", baseTime);
        repository.save(diary);
        flushAndClear();

        Diary loaded = repository.findById(diary.id()).orElseThrow();
        loaded.onLikeAdded();
        loaded.onLikeAdded();
        repository.save(loaded);
        flushAndClear();

        assertThat(repository.findById(diary.id()).orElseThrow().likeCount()).isEqualTo(2);
    }

    @Test
    void public_feed_recent_pages_with_cursor() {
        UUID author = UUID.randomUUID();
        Diary d1 = newPublicDiary(author, "d1", baseTime);
        Diary d2 = newPublicDiary(author, "d2", baseTime.minusSeconds(60));
        Diary d3 = newPublicDiary(author, "d3", baseTime.minusSeconds(120));
        Diary privateD = newDiary(author, "private", baseTime, Visibility.PRIVATE, 0);
        repository.save(d1);
        repository.save(d2);
        repository.save(d3);
        repository.save(privateD);
        flushAndClear();

        // 1st page (size=2)
        List<Diary> page1 = repository.findPublicFeedRecent(Optional.empty(), null, 2);
        assertThat(page1).hasSize(2);
        assertThat(page1.get(0).id()).isEqualTo(d1.id());
        assertThat(page1.get(1).id()).isEqualTo(d2.id());

        // 2nd page using cursor
        RecentFeedCursor cursor = new RecentFeedCursor(d2.createdAt(), d2.id());
        List<Diary> page2 = repository.findPublicFeedRecent(Optional.empty(), cursor, 2);
        assertThat(page2).hasSize(1);
        assertThat(page2.get(0).id()).isEqualTo(d3.id());
        // private 미포함 — visibility=PUBLIC 필터
    }

    @Test
    void public_feed_popular_orders_by_like_count_then_created_at() {
        UUID author = UUID.randomUUID();
        Diary high = newDiary(author, "high", baseTime, Visibility.PUBLIC, 100);
        Diary mid = newDiary(author, "mid", baseTime.minusSeconds(60), Visibility.PUBLIC, 50);
        Diary lowRecent = newDiary(author, "low-r", baseTime.minusSeconds(30), Visibility.PUBLIC, 50);
        repository.save(high);
        repository.save(mid);
        repository.save(lowRecent);
        flushAndClear();

        List<Diary> page = repository.findPublicFeedPopular(Optional.empty(), null, 10);
        assertThat(page).hasSize(3);
        assertThat(page.get(0).id()).isEqualTo(high.id());                  // 100
        assertThat(page.get(1).id()).isEqualTo(lowRecent.id());             // 50, more recent
        assertThat(page.get(2).id()).isEqualTo(mid.id());                   // 50, older
    }

    @Test
    void public_feed_popular_with_cursor_continues() {
        UUID author = UUID.randomUUID();
        Diary high = newDiary(author, "high", baseTime, Visibility.PUBLIC, 100);
        Diary mid = newDiary(author, "mid", baseTime.minusSeconds(60), Visibility.PUBLIC, 50);
        repository.save(high);
        repository.save(mid);
        flushAndClear();

        PopularFeedCursor cursor = new PopularFeedCursor(high.likeCount(), high.createdAt(), high.id());
        List<Diary> page2 = repository.findPublicFeedPopular(Optional.empty(), cursor, 10);
        assertThat(page2).hasSize(1);
        assertThat(page2.get(0).id()).isEqualTo(mid.id());
    }

    @Test
    void public_feed_recent_filtered_by_tag() {
        UUID author = UUID.randomUUID();
        Diary withTag = Diary.create(
            DiaryId.newId(), author, new DiaryContent("ok"),
            ImageUrls.empty(), Tags.ofStrings(List.of("일상", "산책")),
            Visibility.PUBLIC, Clock.fixed(baseTime, ZoneOffset.UTC));
        Diary withoutTag = Diary.create(
            DiaryId.newId(), author, new DiaryContent("ok"),
            ImageUrls.empty(), Tags.ofStrings(List.of("운동")),
            Visibility.PUBLIC, Clock.fixed(baseTime.minusSeconds(60), ZoneOffset.UTC));
        repository.save(withTag);
        repository.save(withoutTag);
        flushAndClear();

        List<Diary> page = repository.findPublicFeedRecent(
            Optional.of(new Tag("일상")), null, 10);
        assertThat(page).hasSize(1);
        assertThat(page.get(0).id()).isEqualTo(withTag.id());
    }

    @Test
    void my_feed_includes_private_and_excludes_others() {
        UUID author = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Diary mine1 = newPublicDiary(author, "mine1", baseTime);
        Diary mine2 = newDiary(author, "mine2", baseTime.minusSeconds(60), Visibility.PRIVATE, 0);
        Diary othersPublic = newPublicDiary(other, "others", baseTime.minusSeconds(120));
        repository.save(mine1);
        repository.save(mine2);
        repository.save(othersPublic);
        flushAndClear();

        List<Diary> page = repository.findMyFeedRecent(author, null, 10);
        assertThat(page).hasSize(2);
        assertThat(page).extracting(Diary::id).containsExactly(mine1.id(), mine2.id());
    }

    // ============================================================
    // PR #79 retrospective M2-M6 (cleanup PR — test 보강)
    // ============================================================

    @Test
    void public_feed_recent_keyset_tiebreak_by_diary_id_when_created_at_equal() {
        // M2 — 같은 created_at 의 두 일기가 있을 때 keyset 인덱스의 보조 정렬 (diary_id desc) 회귀 신호.
        // Flyway V7 의 idx_diary_visibility_created_at_id 인덱스가 created_at 동일 시 diary_id 로 결정적 순서 보장.
        UUID author = UUID.randomUUID();
        // 같은 createdAt 으로 두 diary 생성 — diary_id (UUID) 의 desc 순으로 정렬
        DiaryId idA = DiaryId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        DiaryId idB = DiaryId.of(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
        repository.save(Diary.reconstitute(idA, author, new DiaryContent("a"),
            ImageUrls.empty(), Tags.empty(), Visibility.PUBLIC, 0, 0, baseTime));
        repository.save(Diary.reconstitute(idB, author, new DiaryContent("b"),
            ImageUrls.empty(), Tags.empty(), Visibility.PUBLIC, 0, 0, baseTime));
        flushAndClear();

        List<Diary> page = repository.findPublicFeedRecent(Optional.empty(), null, 10);
        assertThat(page).hasSize(2);
        // diary_id desc — UUID 큰 순서 (idB > idA)
        assertThat(page.get(0).id()).isEqualTo(idB);
        assertThat(page.get(1).id()).isEqualTo(idA);
    }

    /**
     * tag 필터 + cursor 조합 시나리오 (M3/M4).
     *
     * <p><b>한계 (test-reviewer Medium-1)</b>: 본 시나리오는 결과 정확성만 검증. JSON_CONTAINS 는 함수 인덱스
     * 미생성 시 full table scan 으로 동작 — 인덱스 회귀는 별도 EXPLAIN 단언 테스트 필요 (후속 PR 박제).
     */
    @Test
    void public_feed_recent_with_tag_filter_and_cursor_combined() {
        // M3 — tag 필터 + cursor 조합. tag 매칭하면서 cursor 이후 페이지를 정확히 반환.
        UUID author = UUID.randomUUID();
        Diary tagged1 = Diary.create(DiaryId.newId(), author, new DiaryContent("t1"),
            ImageUrls.empty(), Tags.ofStrings(List.of("일상")),
            Visibility.PUBLIC, Clock.fixed(baseTime, ZoneOffset.UTC));
        Diary tagged2 = Diary.create(DiaryId.newId(), author, new DiaryContent("t2"),
            ImageUrls.empty(), Tags.ofStrings(List.of("일상")),
            Visibility.PUBLIC, Clock.fixed(baseTime.minusSeconds(60), ZoneOffset.UTC));
        Diary tagged3 = Diary.create(DiaryId.newId(), author, new DiaryContent("t3"),
            ImageUrls.empty(), Tags.ofStrings(List.of("일상")),
            Visibility.PUBLIC, Clock.fixed(baseTime.minusSeconds(120), ZoneOffset.UTC));
        Diary other = Diary.create(DiaryId.newId(), author, new DiaryContent("o"),
            ImageUrls.empty(), Tags.ofStrings(List.of("운동")),
            Visibility.PUBLIC, Clock.fixed(baseTime.minusSeconds(30), ZoneOffset.UTC));
        repository.save(tagged1);
        repository.save(tagged2);
        repository.save(tagged3);
        repository.save(other);
        flushAndClear();

        // tag=일상 첫 페이지 (size=1) → tagged1
        List<Diary> page1 = repository.findPublicFeedRecent(
            Optional.of(new Tag("일상")), null, 1);
        assertThat(page1).hasSize(1);
        assertThat(page1.get(0).id()).isEqualTo(tagged1.id());

        // tag=일상 + cursor 로 page2 → tagged2
        RecentFeedCursor cursor = new RecentFeedCursor(tagged1.createdAt(), tagged1.id());
        List<Diary> page2 = repository.findPublicFeedRecent(
            Optional.of(new Tag("일상")), cursor, 1);
        assertThat(page2).hasSize(1);
        assertThat(page2.get(0).id()).isEqualTo(tagged2.id());

        // tag=일상 + cursor (tagged2) → tagged3 (other 미포함, tag mismatch)
        RecentFeedCursor cursor2 = new RecentFeedCursor(tagged2.createdAt(), tagged2.id());
        List<Diary> page3 = repository.findPublicFeedRecent(
            Optional.of(new Tag("일상")), cursor2, 10);
        assertThat(page3).hasSize(1);
        assertThat(page3.get(0).id()).isEqualTo(tagged3.id());
    }

    @Test
    void public_feed_popular_with_tag_filter_and_cursor_combined() {
        // M4 — POPULAR sort + tag + cursor 조합.
        UUID author = UUID.randomUUID();
        Diary high = Diary.reconstitute(DiaryId.newId(), author, new DiaryContent("h"),
            ImageUrls.empty(), Tags.ofStrings(List.of("일상")),
            Visibility.PUBLIC, 100, 0, baseTime);
        Diary mid = Diary.reconstitute(DiaryId.newId(), author, new DiaryContent("m"),
            ImageUrls.empty(), Tags.ofStrings(List.of("일상")),
            Visibility.PUBLIC, 50, 0, baseTime.minusSeconds(60));
        Diary other = Diary.reconstitute(DiaryId.newId(), author, new DiaryContent("o"),
            ImageUrls.empty(), Tags.ofStrings(List.of("운동")),
            Visibility.PUBLIC, 200, 0, baseTime);  // 점수 높지만 tag mismatch
        repository.save(high);
        repository.save(mid);
        repository.save(other);
        flushAndClear();

        // tag=일상 첫 페이지 → high (100)
        List<Diary> page1 = repository.findPublicFeedPopular(
            Optional.of(new Tag("일상")), null, 1);
        assertThat(page1).hasSize(1);
        assertThat(page1.get(0).id()).isEqualTo(high.id());

        // cursor (high) 이후 → mid
        PopularFeedCursor cursor = new PopularFeedCursor(high.likeCount(), high.createdAt(), high.id());
        List<Diary> page2 = repository.findPublicFeedPopular(
            Optional.of(new Tag("일상")), cursor, 10);
        assertThat(page2).hasSize(1);
        assertThat(page2.get(0).id()).isEqualTo(mid.id());
    }

    @Test
    void public_feed_popular_likeCount_tied_orders_by_created_at_then_diary_id() {
        // M5 — likeCount 동률 + created_at 동률 → diary_id desc tiebreak (결정적 순서).
        UUID author = UUID.randomUUID();
        DiaryId idA = DiaryId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        DiaryId idB = DiaryId.of(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
        repository.save(Diary.reconstitute(idA, author, new DiaryContent("a"),
            ImageUrls.empty(), Tags.empty(), Visibility.PUBLIC, 50, 0, baseTime));
        repository.save(Diary.reconstitute(idB, author, new DiaryContent("b"),
            ImageUrls.empty(), Tags.empty(), Visibility.PUBLIC, 50, 0, baseTime));
        flushAndClear();

        List<Diary> page = repository.findPublicFeedPopular(Optional.empty(), null, 10);
        assertThat(page).hasSize(2);
        // likeCount + createdAt 동률 → diary_id desc → idB 먼저
        assertThat(page.get(0).id()).isEqualTo(idB);
        assertThat(page.get(1).id()).isEqualTo(idA);
    }

    @Test
    void public_feed_returns_empty_when_no_public_diary_exists() {
        // M6 — 빈 페이지 (visibility=PRIVATE 만 존재) → empty list. cursor 없는 첫 페이지.
        UUID author = UUID.randomUUID();
        repository.save(newDiary(author, "p1", baseTime, Visibility.PRIVATE, 0));
        repository.save(newDiary(author, "p2", baseTime.minusSeconds(60), Visibility.PRIVATE, 0));
        flushAndClear();

        List<Diary> page = repository.findPublicFeedRecent(Optional.empty(), null, 10);
        assertThat(page).isEmpty();

        List<Diary> popularPage = repository.findPublicFeedPopular(Optional.empty(), null, 10);
        assertThat(popularPage).isEmpty();
    }

    @Test
    void deleteAllByAuthorId_removes_all_rows_for_author() {
        UUID author = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        repository.save(newPublicDiary(author, "a1", baseTime));
        repository.save(newPublicDiary(author, "a2", baseTime.minusSeconds(60)));
        repository.save(newPublicDiary(other, "b", baseTime));
        flushAndClear();

        int deleted = repository.deleteAllByAuthorId(author);
        flushAndClear();

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findMyFeedRecent(author, null, 10)).isEmpty();
        assertThat(repository.findMyFeedRecent(other, null, 10)).hasSize(1);
    }

    private Diary newPublicDiary(UUID author, String content, Instant createdAt) {
        return newDiary(author, content, createdAt, Visibility.PUBLIC, 0);
    }

    private Diary newDiary(UUID author, String content, Instant createdAt,
                           Visibility visibility, int likeCount) {
        DiaryId id = DiaryId.newId();
        if (likeCount == 0) {
            return Diary.create(id, author, new DiaryContent(content),
                ImageUrls.empty(), Tags.empty(), visibility,
                Clock.fixed(createdAt, ZoneOffset.UTC));
        }
        return Diary.reconstitute(id, author, new DiaryContent(content),
            ImageUrls.empty(), Tags.empty(), visibility, likeCount, 0, createdAt);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
