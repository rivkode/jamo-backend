package app.backend.jamo.diary.application.service.diary;

import app.backend.jamo.diary.application.dto.diary.GetDiaryCountQuery;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작성자별 일기 수 조회 use case (Slice 3-b / DiaryQueryService.GetDiaryCount).
 *
 * <p>박제: PRD 0526_flutter.md §1.5 (본인 diaryCount) / §1.6 (타인 diaryCount, 공개만).
 *
 * <p>gRPC server ({@code DiaryQueryGrpcService}) 진입 → 본 service. read-only 단순 count.
 * {@code includePrivate} 분기로 본인(전체) / 타인(PUBLIC) 카운트 결정 (IDOR 차단은 Query 플래그 책임).
 */
@Service
@RequiredArgsConstructor
public class GetDiaryCountService {

    private final DiaryRepository diaryRepository;

    @Transactional(readOnly = true)
    public long count(GetDiaryCountQuery query) {
        return query.includePrivate()
            ? diaryRepository.countByAuthorId(query.authorId())
            : diaryRepository.countPublicByAuthorId(query.authorId());
    }
}
