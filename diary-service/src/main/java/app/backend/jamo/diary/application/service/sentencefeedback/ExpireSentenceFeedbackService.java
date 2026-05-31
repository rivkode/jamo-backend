package app.backend.jamo.diary.application.service.sentencefeedback;

import app.backend.jamo.diary.domain.exception.SentenceFeedbackInvalidTransitionException;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackNotFoundException;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedbackId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SUGGESTED 상태에서 expiresAt 초과한 SentenceFeedback 을 EXPIRED 로 전이.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §3 (TTL 24h batch 전이) +
 * decisions/diary/sentence-feedback-batch-decisions.md (전이 주기 / chunk / SKIP LOCKED).
 *
 * <p><b>흐름</b>:
 * <ol>
 *   <li>{@link ExpireSentenceFeedbackTx#findExpirableIds} (writable TX, FOR UPDATE SKIP LOCKED) — chunk 조회</li>
 *   <li>각 id 마다 {@link ExpireSentenceFeedbackTx#expireOne} (write TX) — load → Aggregate.expire(clock) → save</li>
 *   <li>한 row 의 race InvalidTransition (다른 인스턴스가 먼저 처리 / 사용자가 동시에 accept) → log debug + skip,
 *       다른 row 진행</li>
 * </ol>
 *
 * <p><b>self-invocation 회피</b> (code-reviewer C1) — 트랜잭션 경계는 {@link ExpireSentenceFeedbackTx}
 * 별 bean 으로 분리. Spring AOP proxy 가 외부 호출만 가로채므로 같은 클래스 안의 self-call 은
 * {@code @Transactional} 미적용 → OutboxPublisherTx 패턴 정합.
 *
 * <p>EXPIRED 이벤트는 미발행 — 박제 §12 (Requested/Accepted/Rejected 3종) +
 * decisions/diary/sentence-feedback-batch-decisions.md §2 (학습 가치 적음).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpireSentenceFeedbackService {

    private final ExpireSentenceFeedbackTx tx;

    /**
     * 한 사이클 실행 — chunk 1개 처리 후 통계 반환. 다음 사이클은 호출자 (Batch) 가 schedule.
     *
     * @param chunkSize 단일 사이클 처리 상한
     * @return 처리 통계 ({@code candidates / expired / skipped})
     */
    public Result run(int chunkSize) {
        List<SentenceFeedbackId> ids = tx.findExpirableIds(chunkSize);
        if (ids.isEmpty()) {
            return new Result(0, 0, 0);
        }
        int expired = 0;
        int skipped = 0;
        for (SentenceFeedbackId id : ids) {
            try {
                tx.expireOne(id);
                expired++;
            } catch (SentenceFeedbackInvalidTransitionException ex) {
                // race condition — 다른 인스턴스가 먼저 처리 / 사용자가 동시에 accept·reject
                log.debug("EXPIRED skip id={} reason={}", id.asString(), ex.getMessage());
                skipped++;
            } catch (SentenceFeedbackNotFoundException ex) {
                // 이미 cleanup 배치가 삭제 — 안전
                log.debug("EXPIRED skip id={} reason=not_found", id.asString());
                skipped++;
            }
        }
        return new Result(ids.size(), expired, skipped);
    }

    public record Result(int candidates, int expired, int skipped) {
    }
}
