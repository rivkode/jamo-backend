package app.backend.jamo.diary.application.service.diary;

import app.backend.jamo.diary.application.dto.diary.ToggleDiaryLikeCommand;
import app.backend.jamo.diary.application.dto.diary.ToggleDiaryLikeView;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.model.diarylike.DiaryLike;
import app.backend.jamo.diary.domain.repository.DiaryLikeRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * 일기 좋아요 토글 use case.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §8.
 *
 * <p><b>2 Aggregate 동일 트랜잭션</b>: Diary + DiaryLike. likeCount drift 방지 — 사전 존재 체크 + 실제 변화
 * 시에만 {@link Diary#onLikeAdded} / {@link Diary#onLikeRemoved} 호출 + 두 Aggregate 모두 save.
 *
 * <p>멱등성 UPSERT/DELETE:
 * <ul>
 *   <li>liked=true + 없음 → INSERT + likeCount++</li>
 *   <li>liked=true + 있음 → no-op (이미 좋아요 상태)</li>
 *   <li>liked=false + 있음 → DELETE + likeCount--</li>
 *   <li>liked=false + 없음 → no-op (이미 좋아요 취소 상태)</li>
 * </ul>
 *
 * <p>404 통일 (IDOR): 일기 없음 / 비공개+비작성자 모두 404. 자기 일기 좋아요 허용 (자기 비공개 일기에도 가능 —
 * 박제 §8 일관성).
 *
 * <p><b>동시성 race window</b>: 사전 {@code existsByDiaryIdAndUserId} 체크와 {@code save} 사이에 다른 트랜잭션의
 * INSERT 가 commit 되면 본 트랜잭션은 DB unique constraint {@code uk_diary_like_diary_user(diary_id, user_id)}
 * 위반으로 rollback. Infrastructure 슬라이스는 본 unique 제약을 Flyway 마이그레이션에 박아야 한다 (필수). drift
 * 방지의 최후 보루. 호출자 시점 응답: 동시 토글 시 한쪽만 200 (likeCount 정합), 다른 쪽은 5xx (Infrastructure
 * 어댑터에서 catch → 멱등 200 변환은 후속 결정).
 */
@Service
@RequiredArgsConstructor
public class ToggleDiaryLikeService {

    private final DiaryRepository diaryRepository;
    private final DiaryLikeRepository diaryLikeRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ToggleDiaryLikeView toggle(ToggleDiaryLikeCommand command) {
        DiaryId diaryId = DiaryId.of(command.diaryId());

        Diary saved = transactionTemplate.execute(status -> {
            Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new DiaryNotFoundException(
                    "diary not found: " + diaryId.asString()));
            if (!diary.isAccessibleBy(command.userId())) {
                throw new DiaryNotFoundException("diary not found: " + diaryId.asString());
            }

            boolean alreadyLiked = diaryLikeRepository.existsByDiaryIdAndUserId(diaryId, command.userId());

            if (command.liked() && !alreadyLiked) {
                diaryLikeRepository.save(DiaryLike.create(diaryId, command.userId(), clock));
                diary.onLikeAdded();
                diaryRepository.save(diary);
            } else if (!command.liked() && alreadyLiked) {
                diaryLikeRepository.deleteByDiaryIdAndUserId(diaryId, command.userId());
                diary.onLikeRemoved();
                diaryRepository.save(diary);
            }
            return diary;
        });

        return new ToggleDiaryLikeView(diaryId.value(), command.liked(), saved.likeCount());
    }
}
