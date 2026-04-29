package app.backend.jamo.diary.application.service.diary;

import app.backend.jamo.diary.application.cursor.DiaryFeedCursorCodec;
import app.backend.jamo.diary.application.dto.diary.DiaryView;
import app.backend.jamo.diary.application.dto.diary.FeedView;
import app.backend.jamo.diary.application.dto.diary.ListPublicFeedQuery;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryFeedSort;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.repository.DiaryLikeRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import app.backend.jamo.diary.domain.repository.cursor.PopularFeedCursor;
import app.backend.jamo.diary.domain.repository.cursor.RecentFeedCursor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 공개 피드 조회 use case.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §7.
 *
 * <p>RECENT (default) / POPULAR sort 분기. cursor 디코딩은 호출자 (Presentation/Application 진입 단)
 * 책임 — 본 service 는 query 의 cursor 객체 그대로 사용. 응답의 nextCursor 는 본 service 가 인코딩.
 *
 * <p>{@code likedByMe} / {@code authorDisplayName} N+1 회피: 페이지 로드 후 일괄 조회.
 */
@Service
@RequiredArgsConstructor
public class ListPublicFeedService {

    private final DiaryRepository diaryRepository;
    private final DiaryLikeRepository diaryLikeRepository;
    private final UserSummaryPort userSummaryPort;

    @Transactional(readOnly = true)
    public FeedView listPublicFeed(ListPublicFeedQuery query) {
        // 1. 한 페이지 +1 로드 — hasNext 판정 (size=10 요청 시 11 fetch, 11번째가 있으면 hasNext=true)
        int fetchLimit = query.size() + 1;
        List<Diary> fetched = (query.sort() == DiaryFeedSort.RECENT)
            ? diaryRepository.findPublicFeedRecent(query.tag(), query.recentCursor().orElse(null), fetchLimit)
            : diaryRepository.findPublicFeedPopular(query.tag(), query.popularCursor().orElse(null), fetchLimit);

        boolean hasNext = fetched.size() > query.size();
        List<Diary> page = hasNext ? fetched.subList(0, query.size()) : fetched;

        // 2. likedByMe 일괄 조회
        Set<DiaryId> diaryIds = page.stream().map(Diary::id).collect(Collectors.toUnmodifiableSet());
        Set<DiaryId> likedSet = diaryIds.isEmpty()
            ? Set.of()
            : diaryLikeRepository.findDiaryIdsLikedByUser(query.viewerId(), diaryIds);

        // 3. UserSummary 일괄 조회 (작성자 ID 묶음)
        Set<UUID> authorIds = page.stream().map(Diary::authorId).collect(Collectors.toUnmodifiableSet());
        Map<UUID, UserSummaryView> summaries = authorIds.isEmpty()
            ? Map.of()
            : userSummaryPort.batchGet(authorIds);

        // 4. View 조립
        List<DiaryView> items = page.stream()
            .map(d -> DiaryView.from(
                d,
                UserSummaryView.displayNameOrUnknown(Optional.ofNullable(summaries.get(d.authorId()))),
                likedSet.contains(d.id())
            ))
            .toList();

        // 5. nextCursor 인코딩 (마지막 item 기반)
        String nextCursor = null;
        if (hasNext && !page.isEmpty()) {
            Diary last = page.get(page.size() - 1);
            nextCursor = (query.sort() == DiaryFeedSort.RECENT)
                ? DiaryFeedCursorCodec.encodeRecent(new RecentFeedCursor(last.createdAt(), last.id()))
                : DiaryFeedCursorCodec.encodePopular(
                    new PopularFeedCursor(last.likeCount(), last.createdAt(), last.id()));
        }

        return new FeedView(items, nextCursor, hasNext);
    }
}
