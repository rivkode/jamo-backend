package app.backend.jamo.diary.application.service.diary;

import app.backend.jamo.diary.application.cursor.DiaryFeedCursorCodec;
import app.backend.jamo.diary.application.dto.diary.DiaryView;
import app.backend.jamo.diary.application.dto.diary.FeedView;
import app.backend.jamo.diary.application.dto.diary.ListMyFeedQuery;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.repository.DiaryLikeRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import app.backend.jamo.diary.domain.repository.cursor.RecentFeedCursor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 본인 피드 조회 use case.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §7 (RECENT only, public+private 둘 다).
 *
 * <p>본인 일기 → visibility 무관. authorId 가 viewerId 와 동일 — UserSummary 1건만 조회.
 */
@Service
@RequiredArgsConstructor
public class ListMyFeedService {

    private final DiaryRepository diaryRepository;
    private final DiaryLikeRepository diaryLikeRepository;
    private final UserSummaryPort userSummaryPort;

    @Transactional(readOnly = true)
    public FeedView listMyFeed(ListMyFeedQuery query) {
        int fetchLimit = query.size() + 1;
        List<Diary> fetched = diaryRepository.findMyFeedRecent(
            query.authorId(), query.cursor().orElse(null), fetchLimit
        );

        boolean hasNext = fetched.size() > query.size();
        List<Diary> page = hasNext ? fetched.subList(0, query.size()) : fetched;

        Set<DiaryId> diaryIds = page.stream().map(Diary::id).collect(Collectors.toUnmodifiableSet());
        Set<DiaryId> likedSet = diaryIds.isEmpty()
            ? Set.of()
            : diaryLikeRepository.findDiaryIdsLikedByUser(query.authorId(), diaryIds);

        // 본인 피드 — 작성자 1명, UserSummary 단건 (없으면 fallback — identity-service 일시 장애 시그널)
        String displayName = page.isEmpty()
            ? UserSummaryView.UNKNOWN_DISPLAY_NAME
            : UserSummaryView.displayNameOrUnknown(userSummaryPort.get(query.authorId()));

        List<DiaryView> items = page.stream()
            .map(d -> DiaryView.from(d, displayName, likedSet.contains(d.id())))
            .toList();

        String nextCursor = null;
        if (hasNext && !page.isEmpty()) {
            Diary last = page.get(page.size() - 1);
            nextCursor = DiaryFeedCursorCodec.encodeRecent(
                new RecentFeedCursor(last.createdAt(), last.id()));
        }

        return new FeedView(items, nextCursor, hasNext);
    }
}
