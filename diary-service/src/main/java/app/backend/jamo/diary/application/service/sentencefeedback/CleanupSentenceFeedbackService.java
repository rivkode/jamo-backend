package app.backend.jamo.diary.application.service.sentencefeedback;

import app.backend.jamo.diary.domain.repository.SentenceFeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedbackId;

/**
 * 90일 보존 cleanup — final 상태 (ACCEPTED/REJECTED/EXPIRED/FAILED) + decidedAt < (now - retention) 인
 * row hard-delete.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §14 + sentence-feedback-batch-decisions.md.
 *
 * <p>회원 탈퇴 즉시 삭제는 별 경로 — `UserWithdrawalRequestedListener` (PR #71) 가 처리. 본 service 는
 * 일반 retention 한정.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CleanupSentenceFeedbackService {

    private final SentenceFeedbackRepository repository;
    private final Clock clock;

    /**
     * 한 사이클 실행 — chunk 1건 조회 후 일괄 hard-delete (단건 트랜잭션).
     *
     * @param retentionDays 보존 기간 (일)
     * @param chunkSize     단일 사이클 삭제 상한
     * @return 실제 삭제된 row 수
     */
    @Transactional
    public int run(int retentionDays, int chunkSize) {
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(retentionDays));
        List<SentenceFeedbackId> ids = repository.findFinalOlderThan(cutoff, chunkSize);
        if (ids.isEmpty()) {
            return 0;
        }
        return repository.deleteByIds(ids);
    }
}
