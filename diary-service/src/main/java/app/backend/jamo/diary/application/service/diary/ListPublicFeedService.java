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
import app.backend.jamo.diary.domain.model.diary.Tag;
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
 * <p><b>책임 재배치 (cleanup PR — code-reviewer M1/M5)</b>: raw tag/sort/cursor (nullable String) 을 받아
 * 본 service 가 도메인 VO 조립 / sort enum 변환 / cursor codec 호출을 일괄 처리. invariant 위반 시 도메인
 * 예외 ({@code InvalidTagException} / {@code InvalidDiaryFeedCursorException} / {@code IllegalArgumentException})
 * 그대로 전파 → ExceptionHandler 400 매핑.
 *
 * <p>RECENT (default) / POPULAR sort 분기. {@code likedByMe} / {@code authorDisplayName} N+1 회피: 페이지
 * 로드 후 일괄 조회.
 */
@Service
@RequiredArgsConstructor
public class ListPublicFeedService {

    private final DiaryRepository diaryRepository;
    private final DiaryLikeRepository diaryLikeRepository;
    private final UserSummaryPort userSummaryPort;

    @Transactional(readOnly = true)
    public FeedView listPublicFeed(ListPublicFeedQuery query) {
        // 1. raw → domain 조립 (책임 재배치 — Controller 에서 이전)
        DiaryFeedSort sort = resolveSort(query.sortOrNull());
        Optional<Tag> tag = resolveTag(query.tagOrNull());
        Optional<RecentFeedCursor> recentCursor = (sort == DiaryFeedSort.RECENT)
            ? resolveRecentCursor(query.cursorOrNull())
            : Optional.empty();
        Optional<PopularFeedCursor> popularCursor = (sort == DiaryFeedSort.POPULAR)
            ? resolvePopularCursor(query.cursorOrNull())
            : Optional.empty();

        // 2. 한 페이지 +1 로드 — hasNext 판정
        int fetchLimit = query.size() + 1;
        List<Diary> fetched = (sort == DiaryFeedSort.RECENT)
            ? diaryRepository.findPublicFeedRecent(tag, recentCursor.orElse(null), fetchLimit)
            : diaryRepository.findPublicFeedPopular(tag, popularCursor.orElse(null), fetchLimit);

        boolean hasNext = fetched.size() > query.size();
        List<Diary> page = hasNext ? fetched.subList(0, query.size()) : fetched;

        // 3. likedByMe 일괄 조회
        Set<DiaryId> diaryIds = page.stream().map(Diary::id).collect(Collectors.toUnmodifiableSet());
        Set<DiaryId> likedSet = diaryIds.isEmpty()
            ? Set.of()
            : diaryLikeRepository.findDiaryIdsLikedByUser(query.viewerId(), diaryIds);

        // 4. UserSummary 일괄 조회 (작성자 ID 묶음)
        Set<UUID> authorIds = page.stream().map(Diary::authorId).collect(Collectors.toUnmodifiableSet());
        Map<UUID, UserSummaryView> summaries = authorIds.isEmpty()
            ? Map.of()
            : userSummaryPort.batchGet(authorIds);

        // 5. View 조립
        List<DiaryView> items = page.stream()
            .map(d -> DiaryView.from(
                d,
                UserSummaryView.displayNameOrUnknown(Optional.ofNullable(summaries.get(d.authorId()))),
                likedSet.contains(d.id())
            ))
            .toList();

        // 6. nextCursor 인코딩 (마지막 item 기반)
        String nextCursor = null;
        if (hasNext && !page.isEmpty()) {
            Diary last = page.get(page.size() - 1);
            nextCursor = (sort == DiaryFeedSort.RECENT)
                ? DiaryFeedCursorCodec.encodeRecent(new RecentFeedCursor(last.createdAt(), last.id()))
                : DiaryFeedCursorCodec.encodePopular(
                    new PopularFeedCursor(last.likeCount(), last.createdAt(), last.id()));
        }

        return new FeedView(items, nextCursor, hasNext);
    }

    /** sort null → default RECENT. case-insensitive. 알 수 없는 값 → IllegalArgumentException → 400. */
    private static DiaryFeedSort resolveSort(String sortOrNull) {
        if (sortOrNull == null || sortOrNull.isBlank()) {
            return DiaryFeedSort.defaultSort();
        }
        return DiaryFeedSort.valueOf(sortOrNull.toUpperCase());
    }

    /** tag null/blank → no filter. invariant 위반 → InvalidTagException → 400. */
    private static Optional<Tag> resolveTag(String tagOrNull) {
        if (tagOrNull == null || tagOrNull.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Tag(tagOrNull));
    }

    private static Optional<RecentFeedCursor> resolveRecentCursor(String cursorOrNull) {
        if (cursorOrNull == null || cursorOrNull.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(DiaryFeedCursorCodec.decodeRecent(cursorOrNull));
    }

    private static Optional<PopularFeedCursor> resolvePopularCursor(String cursorOrNull) {
        if (cursorOrNull == null || cursorOrNull.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(DiaryFeedCursorCodec.decodePopular(cursorOrNull));
    }
}
