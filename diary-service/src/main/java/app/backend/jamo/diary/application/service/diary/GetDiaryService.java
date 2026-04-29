package app.backend.jamo.diary.application.service.diary;

import app.backend.jamo.diary.application.dto.diary.DiaryView;
import app.backend.jamo.diary.application.dto.diary.GetDiaryQuery;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.repository.DiaryLikeRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일기 단건 조회 use case.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §2 (404 통일 IDOR) / §4 (likedByMe viewer-context) / §11 (조회수 미지원).
 *
 * <p>비공개 + 비작성자 → 404 (자원 존재 비노출). 자원 부재 → 404. 두 케이스 동일 응답.
 */
@Service
@RequiredArgsConstructor
public class GetDiaryService {

    private final DiaryRepository diaryRepository;
    private final DiaryLikeRepository diaryLikeRepository;
    private final UserSummaryPort userSummaryPort;

    @Transactional(readOnly = true)
    public DiaryView get(GetDiaryQuery query) {
        DiaryId diaryId = DiaryId.of(query.diaryId());
        Diary diary = diaryRepository.findById(diaryId)
            .orElseThrow(() -> new DiaryNotFoundException("diary not found: " + diaryId.asString()));
        if (!diary.isAccessibleBy(query.viewerId())) {
            throw new DiaryNotFoundException("diary not found: " + diaryId.asString());
        }

        boolean likedByMe = diaryLikeRepository.existsByDiaryIdAndUserId(diaryId, query.viewerId());
        String displayName = UserSummaryView.displayNameOrUnknown(
            userSummaryPort.get(diary.authorId()));

        return DiaryView.from(diary, displayName, likedByMe);
    }
}
