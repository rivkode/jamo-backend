package app.backend.jamo.diary.application.service.diary;

import app.backend.jamo.diary.application.cursor.DiaryFeedCursorCodec;
import app.backend.jamo.diary.application.cursor.InvalidDiaryFeedCursorException;
import app.backend.jamo.diary.application.dto.diary.FeedView;
import app.backend.jamo.diary.application.dto.diary.ListPublicFeedQuery;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import app.backend.jamo.diary.domain.exception.InvalidTagException;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryFeedSort;
import app.backend.jamo.diary.domain.model.diary.Tag;
import app.backend.jamo.diary.domain.repository.DiaryLikeRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import app.backend.jamo.diary.domain.repository.cursor.PopularFeedCursor;
import app.backend.jamo.diary.domain.repository.cursor.RecentFeedCursor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")  // ArgumentCaptor.forClass(Optional.class) raw cast — Mockito 내부 type erasure 한계
class ListPublicFeedServiceTest {

    private DiaryRepository diaryRepository;
    private DiaryLikeRepository diaryLikeRepository;
    private UserSummaryPort userSummaryPort;
    private ListPublicFeedService service;

    @BeforeEach
    void setUp() {
        diaryRepository = mock(DiaryRepository.class);
        diaryLikeRepository = mock(DiaryLikeRepository.class);
        userSummaryPort = mock(UserSummaryPort.class);
        service = new ListPublicFeedService(diaryRepository, diaryLikeRepository, userSummaryPort);
    }

    @Test
    void recent_with_full_page_signals_hasNext_and_encodes_cursor() {
        UUID viewer = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        Instant base = Instant.parse("2026-04-30T10:00:00Z");
        Diary d1 = DiaryTestFixtures.publicDiaryAt(author, base, 5);
        Diary d2 = DiaryTestFixtures.publicDiaryAt(author, base.minusSeconds(60), 3);
        Diary d3 = DiaryTestFixtures.publicDiaryAt(author, base.minusSeconds(120), 1);
        when(diaryRepository.findPublicFeedRecent(any(), any(), eq(3)))
            .thenReturn(List.of(d1, d2, d3));
        when(diaryLikeRepository.findDiaryIdsLikedByUser(eq(viewer), anySet()))
            .thenReturn(Set.of(d1.id()));
        when(userSummaryPort.batchGet(anySet()))
            .thenReturn(Map.of(author, new UserSummaryView(author, "홍길동")));

        FeedView feed = service.listPublicFeed(new ListPublicFeedQuery(
            viewer, null, null, null, 2));

        assertThat(feed.items()).hasSize(2);
        assertThat(feed.hasNext()).isTrue();
        assertThat(feed.nextCursor()).isNotBlank();
        assertThat(feed.items().get(0).likedByMe()).isTrue();
        assertThat(feed.items().get(1).likedByMe()).isFalse();
        assertThat(feed.items().get(0).authorDisplayName()).isEqualTo("홍길동");
    }

    @Test
    void recent_with_under_page_returns_no_next_cursor() {
        UUID viewer = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        Diary only = DiaryTestFixtures.publicDiaryAt(author, DiaryTestFixtures.NOW, 0);
        when(diaryRepository.findPublicFeedRecent(any(), any(), eq(11)))
            .thenReturn(List.of(only));
        when(diaryLikeRepository.findDiaryIdsLikedByUser(any(), anySet())).thenReturn(Set.of());
        when(userSummaryPort.batchGet(anySet()))
            .thenReturn(Map.of(author, new UserSummaryView(author, "홍")));

        FeedView feed = service.listPublicFeed(new ListPublicFeedQuery(
            viewer, null, null, null, 10));

        assertThat(feed.items()).hasSize(1);
        assertThat(feed.hasNext()).isFalse();
        assertThat(feed.nextCursor()).isNull();
    }

    @Test
    void empty_page_returns_empty_view() {
        UUID viewer = UUID.randomUUID();
        when(diaryRepository.findPublicFeedRecent(any(), any(), any(Integer.class)))
            .thenReturn(List.of());

        FeedView feed = service.listPublicFeed(new ListPublicFeedQuery(
            viewer, null, null, null, 10));

        assertThat(feed.items()).isEmpty();
        assertThat(feed.hasNext()).isFalse();
        assertThat(feed.nextCursor()).isNull();
    }

    @Test
    void popular_uses_popular_query_branch() {
        UUID viewer = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        Diary d = DiaryTestFixtures.publicDiaryAt(author, DiaryTestFixtures.NOW, 99);
        when(diaryRepository.findPublicFeedPopular(any(), any(), any(Integer.class)))
            .thenReturn(List.of(d));
        when(diaryLikeRepository.findDiaryIdsLikedByUser(any(), anySet())).thenReturn(Set.of());
        when(userSummaryPort.batchGet(anySet()))
            .thenReturn(Map.of(author, new UserSummaryView(author, "홍")));

        FeedView feed = service.listPublicFeed(new ListPublicFeedQuery(
            viewer, null, "popular", null, 10));

        assertThat(feed.items()).hasSize(1);
        assertThat(feed.items().get(0).likeCount()).isEqualTo(99);
        verify(diaryRepository, never()).findPublicFeedRecent(any(), any(), any(Integer.class));
    }

    // ============================================================
    // 책임 재배치 (cleanup PR — code-reviewer M1/M5)
    // ============================================================

    @Test
    void sort_null_defaults_to_RECENT() {
        UUID viewer = UUID.randomUUID();
        when(diaryRepository.findPublicFeedRecent(any(), any(), any(Integer.class)))
            .thenReturn(List.of());

        service.listPublicFeed(new ListPublicFeedQuery(viewer, null, null, null, 10));

        verify(diaryRepository).findPublicFeedRecent(any(), any(), eq(11));
    }

    @Test
    void sort_lowercase_recent_works_via_toUpperCase() {
        UUID viewer = UUID.randomUUID();
        when(diaryRepository.findPublicFeedRecent(any(), any(), any(Integer.class)))
            .thenReturn(List.of());

        service.listPublicFeed(new ListPublicFeedQuery(viewer, null, "recent", null, 10));

        verify(diaryRepository).findPublicFeedRecent(any(), any(), eq(11));
    }

    @Test
    void sort_unknown_value_throws_IllegalArgumentException() {
        // ExceptionHandler 가 IAE → 400 매핑
        UUID viewer = UUID.randomUUID();

        assertThatThrownBy(() -> service.listPublicFeed(
            new ListPublicFeedQuery(viewer, null, "trending", null, 10)))
            .isInstanceOf(IllegalArgumentException.class);
        verify(diaryRepository, never()).findPublicFeedRecent(any(), any(), any(Integer.class));
    }

    @Test
    void tag_blank_treated_as_no_filter() {
        UUID viewer = UUID.randomUUID();
        when(diaryRepository.findPublicFeedRecent(any(), any(), any(Integer.class)))
            .thenReturn(List.of());

        service.listPublicFeed(new ListPublicFeedQuery(viewer, "   ", null, null, 10));

        ArgumentCaptor<Optional<Tag>> tagCaptor = ArgumentCaptor.forClass(Optional.class);
        verify(diaryRepository).findPublicFeedRecent(tagCaptor.capture(), any(), eq(11));
        assertThat(tagCaptor.getValue()).isEmpty();
    }

    @Test
    void tag_assembled_into_Tag_VO_when_provided() {
        UUID viewer = UUID.randomUUID();
        when(diaryRepository.findPublicFeedRecent(any(), any(), any(Integer.class)))
            .thenReturn(List.of());

        service.listPublicFeed(new ListPublicFeedQuery(viewer, "일상", null, null, 10));

        ArgumentCaptor<Optional<Tag>> tagCaptor = ArgumentCaptor.forClass(Optional.class);
        verify(diaryRepository).findPublicFeedRecent(tagCaptor.capture(), any(), eq(11));
        assertThat(tagCaptor.getValue()).isPresent();
        assertThat(tagCaptor.getValue().orElseThrow().value()).isEqualTo("일상");
    }

    @Test
    void tag_invariant_violation_throws_InvalidTagException() {
        // ExceptionHandler 가 InvalidTagException → 400 매핑. Service 단에서 Tag VO 조립 시 발생.
        UUID viewer = UUID.randomUUID();
        String tooLong = "x".repeat(31);  // domain 한도 30 cp 초과

        assertThatThrownBy(() -> service.listPublicFeed(
            new ListPublicFeedQuery(viewer, tooLong, null, null, 10)))
            .isInstanceOf(InvalidTagException.class);
        verify(diaryRepository, never()).findPublicFeedRecent(any(), any(), any(Integer.class));
    }

    @Test
    void cursor_invalid_base64_throws_InvalidDiaryFeedCursorException() {
        UUID viewer = UUID.randomUUID();

        assertThatThrownBy(() -> service.listPublicFeed(
            new ListPublicFeedQuery(viewer, null, null, "***not-base64***", 10)))
            .isInstanceOf(InvalidDiaryFeedCursorException.class);
        verify(diaryRepository, never()).findPublicFeedRecent(any(), any(), any(Integer.class));
    }

    @Test
    void recent_cursor_decoded_into_recentCursor_slot() {
        UUID viewer = UUID.randomUUID();
        UUID diaryUuid = UUID.randomUUID();
        Instant lastCreated = Instant.parse("2026-04-30T10:00:00Z");
        String cursor = DiaryFeedCursorCodec.encodeRecent(new RecentFeedCursor(
            lastCreated,
            app.backend.jamo.diary.domain.model.diary.DiaryId.of(diaryUuid)));
        when(diaryRepository.findPublicFeedRecent(any(), any(), any(Integer.class)))
            .thenReturn(List.of());

        service.listPublicFeed(new ListPublicFeedQuery(viewer, null, null, cursor, 10));

        ArgumentCaptor<RecentFeedCursor> captor = ArgumentCaptor.forClass(RecentFeedCursor.class);
        verify(diaryRepository).findPublicFeedRecent(any(), captor.capture(), eq(11));
        assertThat(captor.getValue().lastCreatedAt()).isEqualTo(lastCreated);
        assertThat(captor.getValue().lastDiaryId().value()).isEqualTo(diaryUuid);
    }

    @Test
    void popular_cursor_decoded_into_popularCursor_slot() {
        UUID viewer = UUID.randomUUID();
        UUID diaryUuid = UUID.randomUUID();
        Instant lastCreated = Instant.parse("2026-04-30T10:00:00Z");
        String cursor = DiaryFeedCursorCodec.encodePopular(new PopularFeedCursor(
            42,
            lastCreated,
            app.backend.jamo.diary.domain.model.diary.DiaryId.of(diaryUuid)));
        when(diaryRepository.findPublicFeedPopular(any(), any(), any(Integer.class)))
            .thenReturn(List.of());

        service.listPublicFeed(new ListPublicFeedQuery(viewer, null, "popular", cursor, 10));

        ArgumentCaptor<PopularFeedCursor> captor = ArgumentCaptor.forClass(PopularFeedCursor.class);
        verify(diaryRepository).findPublicFeedPopular(any(), captor.capture(), eq(11));
        assertThat(captor.getValue().lastLikeCount()).isEqualTo(42);
        assertThat(captor.getValue().lastCreatedAt()).isEqualTo(lastCreated);
        assertThat(captor.getValue().lastDiaryId().value()).isEqualTo(diaryUuid);
    }

    @Test
    void sort_null_with_popular_format_cursor_throws_InvalidCursor() {
        // test-reviewer Medium-2 — sort 미지정 (default RECENT) 인데 cursor 가 POPULAR 형식이면
        // RecentFeedCursor 디코딩 실패 → InvalidDiaryFeedCursorException → 400. UI 가 sort 변경 시 cursor
        // 를 reset 하지 않는 버그 회귀 신호.
        UUID viewer = UUID.randomUUID();
        UUID diaryUuid = UUID.randomUUID();
        String popularCursor = DiaryFeedCursorCodec.encodePopular(new PopularFeedCursor(
            42,
            Instant.parse("2026-04-30T10:00:00Z"),
            app.backend.jamo.diary.domain.model.diary.DiaryId.of(diaryUuid)));

        assertThatThrownBy(() -> service.listPublicFeed(
            new ListPublicFeedQuery(viewer, null, null, popularCursor, 10)))
            .isInstanceOf(InvalidDiaryFeedCursorException.class);
    }

    @Test
    void sort_popular_with_recent_format_cursor_throws_InvalidCursor() {
        // 대칭 시나리오 — sort=popular 인데 cursor 가 RECENT 형식이면 디코딩 실패 → 400.
        UUID viewer = UUID.randomUUID();
        UUID diaryUuid = UUID.randomUUID();
        String recentCursor = DiaryFeedCursorCodec.encodeRecent(new RecentFeedCursor(
            Instant.parse("2026-04-30T10:00:00Z"),
            app.backend.jamo.diary.domain.model.diary.DiaryId.of(diaryUuid)));

        assertThatThrownBy(() -> service.listPublicFeed(
            new ListPublicFeedQuery(viewer, null, "popular", recentCursor, 10)))
            .isInstanceOf(InvalidDiaryFeedCursorException.class);
    }

    @Test
    void size_out_of_range_throws_at_query_construction() {
        UUID viewer = UUID.randomUUID();
        assertThatThrownBy(() -> new ListPublicFeedQuery(viewer, null, null, null, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ListPublicFeedQuery(viewer, null, null, null, 101))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
