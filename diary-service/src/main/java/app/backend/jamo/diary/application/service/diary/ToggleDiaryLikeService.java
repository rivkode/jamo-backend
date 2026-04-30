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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
 * <p><b>동시성 race window + 멱등 fallback</b> (cleanup PR — code-reviewer H1, PR #79 retrospective):
 * 사전 {@code existsByDiaryIdAndUserId} 체크와 {@code save} 사이에 다른 트랜잭션의 INSERT 가 commit 되면
 * 본 트랜잭션은 DB unique constraint {@code uk_diary_like_diary_user(diary_id, user_id)} 위반으로
 * {@link DataIntegrityViolationException} 발생 → rollback. 이후 본 service 가 별도 트랜잭션에서 Diary
 * 를 재로드 + 실제 like 상태를 재조회해 다른 tx commit 후 진실을 반영한 멱등 200 응답.
 *
 * <p>liked=false + DELETE 는 race 무관 (없는 row 를 지워도 0 row 영향, 예외 없음). 따라서 본 catch 블록은
 * 사실상 liked=true 시에만 진입.
 *
 * <p><b>fallback 응답의 정합성 (code-reviewer H1)</b>: {@code liked} 필드는 {@code command.liked()} 를 그대로
 * echo 하지 않고 fallback 트랜잭션 안에서 {@code existsByDiaryIdAndUserId} 를 다시 호출해 실제 DB 상태를
 * 노출. race 후 또 다른 tx 가 DELETE 까지 한 극단 케이스에서도 응답과 실제 상태가 일치 — "결과는 결국 한쪽
 * 상태로 수렴" UX 보장.
 *
 * <p>fallback 트랜잭션은 read-only 작업만 수행 (read-write 트랜잭션이지만 실제 변경 없음). 별도 read-only
 * {@code TransactionTemplate} bean 분리는 후속 PR 박제.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToggleDiaryLikeService {

    private final DiaryRepository diaryRepository;
    private final DiaryLikeRepository diaryLikeRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ToggleDiaryLikeView toggle(ToggleDiaryLikeCommand command) {
        DiaryId diaryId = DiaryId.of(command.diaryId());

        try {
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
        } catch (DataIntegrityViolationException e) {
            // 동시성 race — 다른 tx 가 먼저 INSERT, 본 tx UNIQUE 위반. 멱등 fallback: 별도 트랜잭션
            // 에서 Diary 재로드 + 실제 like 상태 재조회. 본 tx 의 onLikeAdded 는 rollback 되지만 다른
            // tx 가 이미 likeCount 를 sync 했으므로 결과 정합성 유지.
            log.warn("Toggle like race detected — falling back to idempotent re-read: diaryId={} userId={}",
                diaryId.asString(), command.userId());
            return transactionTemplate.execute(status -> {
                Diary reloaded = diaryRepository.findById(diaryId)
                    .orElseThrow(() -> new DiaryNotFoundException(
                        "diary not found after race: " + diaryId.asString()));
                // code-reviewer H1 — command.liked echo 가 아닌 DB 진실 노출.
                boolean actualLiked = diaryLikeRepository.existsByDiaryIdAndUserId(
                    diaryId, command.userId());
                return new ToggleDiaryLikeView(diaryId.value(), actualLiked, reloaded.likeCount());
            });
        }
    }
}
