package app.backend.jamo.diary.application.service.diary;

import app.backend.jamo.diary.application.dto.diary.FeedView;
import app.backend.jamo.diary.application.dto.diary.ListMyFeedQuery;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.repository.DiaryLikeRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListMyFeedServiceTest {

    private DiaryRepository diaryRepository;
    private DiaryLikeRepository diaryLikeRepository;
    private UserSummaryPort userSummaryPort;
    private ListMyFeedService service;

    @BeforeEach
    void setUp() {
        diaryRepository = mock(DiaryRepository.class);
        diaryLikeRepository = mock(DiaryLikeRepository.class);
        userSummaryPort = mock(UserSummaryPort.class);
        service = new ListMyFeedService(diaryRepository, diaryLikeRepository, userSummaryPort);
    }

    @Test
    void happy_returns_my_diaries_with_single_user_summary() {
        UUID author = UUID.randomUUID();
        Diary d1 = DiaryTestFixtures.publicDiaryAt(author, DiaryTestFixtures.NOW, 0);
        Diary d2 = DiaryTestFixtures.publicDiaryAt(author, DiaryTestFixtures.NOW.minusSeconds(60), 0);
        when(diaryRepository.findMyFeedRecent(eq(author), any(), eq(11)))
            .thenReturn(List.of(d1, d2));
        when(diaryLikeRepository.findDiaryIdsLikedByUser(eq(author), anySet()))
            .thenReturn(Set.of(d1.id()));
        when(userSummaryPort.get(author))
            .thenReturn(Optional.of(new UserSummaryView(author, "내")));

        FeedView feed = service.listMyFeed(new ListMyFeedQuery(author, null, 10));

        assertThat(feed.items()).hasSize(2);
        assertThat(feed.hasNext()).isFalse();
        assertThat(feed.items().get(0).authorDisplayName()).isEqualTo("내");
        assertThat(feed.items().get(0).likedByMe()).isTrue();
        assertThat(feed.items().get(1).likedByMe()).isFalse();
    }

    @Test
    void empty_page_does_not_call_user_summary() {
        UUID author = UUID.randomUUID();
        when(diaryRepository.findMyFeedRecent(any(), any(), any(Integer.class)))
            .thenReturn(List.of());

        FeedView feed = service.listMyFeed(new ListMyFeedQuery(author, null, 10));

        assertThat(feed.items()).isEmpty();
        verify(userSummaryPort, never()).get(any());
    }

    @Test
    void my_feed_includes_private_diaries() {
        // 박제 §7 — 본인 = visibility 무관 (public + private 둘 다 포함)
        UUID author = UUID.randomUUID();
        app.backend.jamo.diary.domain.model.diary.Diary publicD = DiaryTestFixtures.publicDiaryAt(author, DiaryTestFixtures.NOW, 0);
        app.backend.jamo.diary.domain.model.diary.Diary privateD = DiaryTestFixtures.privateDiary(author);
        when(diaryRepository.findMyFeedRecent(any(), any(), eq(11)))
            .thenReturn(List.of(publicD, privateD));
        when(diaryLikeRepository.findDiaryIdsLikedByUser(any(), anySet())).thenReturn(Set.of());
        when(userSummaryPort.get(author)).thenReturn(Optional.of(new UserSummaryView(author, "내")));

        FeedView feed = service.listMyFeed(new ListMyFeedQuery(author, null, 10));

        assertThat(feed.items()).hasSize(2);
        assertThat(feed.items())
            .extracting(app.backend.jamo.diary.application.dto.diary.DiaryView::visibility)
            .containsExactlyInAnyOrder(
                app.backend.jamo.diary.domain.model.diary.Visibility.PUBLIC,
                app.backend.jamo.diary.domain.model.diary.Visibility.PRIVATE);
    }

    @Test
    void hasNext_true_when_size_plus_one_returned() {
        UUID author = UUID.randomUUID();
        Diary d1 = DiaryTestFixtures.publicDiaryAt(author, DiaryTestFixtures.NOW, 0);
        Diary d2 = DiaryTestFixtures.publicDiaryAt(author, DiaryTestFixtures.NOW.minusSeconds(60), 0);
        Diary d3 = DiaryTestFixtures.publicDiaryAt(author, DiaryTestFixtures.NOW.minusSeconds(120), 0);
        when(diaryRepository.findMyFeedRecent(any(), any(), eq(3)))
            .thenReturn(List.of(d1, d2, d3));
        when(diaryLikeRepository.findDiaryIdsLikedByUser(any(), anySet())).thenReturn(Set.of());
        when(userSummaryPort.get(author)).thenReturn(Optional.of(new UserSummaryView(author, "내")));

        FeedView feed = service.listMyFeed(new ListMyFeedQuery(author, null, 2));

        assertThat(feed.items()).hasSize(2);
        assertThat(feed.hasNext()).isTrue();
        assertThat(feed.nextCursor()).isNotBlank();
    }

    // ============================================================
    // 책임 재배치 (cleanup PR — code-reviewer M1/M5)
    // ============================================================

    @Test
    void cursor_invalid_base64_throws_InvalidDiaryFeedCursorException() {
        // ExceptionHandler 가 InvalidDiaryFeedCursorException → 400 매핑
        UUID author = UUID.randomUUID();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.listMyFeed(
                new ListMyFeedQuery(author, "***not-base64***", 10)))
            .isInstanceOf(app.backend.jamo.diary.application.cursor.InvalidDiaryFeedCursorException.class);
        verify(diaryRepository, never()).findMyFeedRecent(any(), any(), any(Integer.class));
    }

    @Test
    void cursor_decoded_into_recent_cursor() {
        UUID author = UUID.randomUUID();
        UUID diaryUuid = UUID.randomUUID();
        Instant lastCreated = Instant.parse("2026-04-30T10:00:00Z");
        String cursor = app.backend.jamo.diary.application.cursor.DiaryFeedCursorCodec.encodeRecent(
            new app.backend.jamo.diary.domain.repository.cursor.RecentFeedCursor(
                lastCreated,
                app.backend.jamo.diary.domain.model.diary.DiaryId.of(diaryUuid)));
        when(diaryRepository.findMyFeedRecent(any(), any(), any(Integer.class)))
            .thenReturn(List.of());

        service.listMyFeed(new ListMyFeedQuery(author, cursor, 10));

        org.mockito.ArgumentCaptor<app.backend.jamo.diary.domain.repository.cursor.RecentFeedCursor> captor =
            org.mockito.ArgumentCaptor.forClass(
                app.backend.jamo.diary.domain.repository.cursor.RecentFeedCursor.class);
        verify(diaryRepository).findMyFeedRecent(eq(author), captor.capture(), eq(11));
        assertThat(captor.getValue().lastCreatedAt()).isEqualTo(lastCreated);
        assertThat(captor.getValue().lastDiaryId().value()).isEqualTo(diaryUuid);
    }

    @Test
    void blank_cursor_treated_as_no_cursor() {
        UUID author = UUID.randomUUID();
        when(diaryRepository.findMyFeedRecent(any(), any(), any(Integer.class)))
            .thenReturn(List.of());

        service.listMyFeed(new ListMyFeedQuery(author, "  ", 10));

        org.mockito.ArgumentCaptor<app.backend.jamo.diary.domain.repository.cursor.RecentFeedCursor> captor =
            org.mockito.ArgumentCaptor.forClass(
                app.backend.jamo.diary.domain.repository.cursor.RecentFeedCursor.class);
        verify(diaryRepository).findMyFeedRecent(eq(author), captor.capture(), eq(11));
        assertThat(captor.getValue()).isNull();
    }

    @Test
    void size_out_of_range_throws_at_query_construction() {
        UUID author = UUID.randomUUID();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new ListMyFeedQuery(author, null, 0))
            .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new ListMyFeedQuery(author, null, 101))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
