package app.backend.jamo.diary.application.service.diary;

import app.backend.jamo.diary.application.dto.diary.FeedView;
import app.backend.jamo.diary.application.dto.diary.ListPublicFeedQuery;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryFeedSort;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.repository.DiaryLikeRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        // size=2 요청 → fetch 3 (size+1)
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
            viewer, Optional.empty(), DiaryFeedSort.RECENT, Optional.empty(), Optional.empty(), 2
        ));

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
            viewer, Optional.empty(), DiaryFeedSort.RECENT, Optional.empty(), Optional.empty(), 10
        ));

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
            viewer, Optional.empty(), DiaryFeedSort.RECENT, Optional.empty(), Optional.empty(), 10
        ));

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
            viewer, Optional.empty(), DiaryFeedSort.POPULAR, Optional.empty(), Optional.empty(), 10
        ));

        assertThat(feed.items()).hasSize(1);
        assertThat(feed.items().get(0).likeCount()).isEqualTo(99);
    }
}
