package app.backend.jamo.diary.application.service.sentencefeedback;

import app.backend.jamo.diary.domain.exception.SentenceFeedbackNotFoundException;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedback;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedbackId;
import app.backend.jamo.diary.domain.repository.SentenceFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * EXPIRED 전이 batch 의 트랜잭션 경계 분리 — {@link ExpireSentenceFeedbackService} 의
 * {@code run()} 가 같은 클래스의 {@code expireOne} 을 self-invocation 하면 Spring AOP proxy 우회
 * 로 {@code @Transactional} 미적용 (code-reviewer C1).
 *
 * <p>OutboxPublisherTx 패턴 정합 — 별 bean 으로 트랜잭션 경계 분리:
 * <ul>
 *   <li>{@link #findExpirableIds(int)} — readOnly TX 안에서 native SQL {@code FOR UPDATE SKIP LOCKED}
 *       호출 (auto-commit 시 SKIP LOCKED 잠금 즉시 해제 회피)</li>
 *   <li>{@link #expireOne(SentenceFeedbackId)} — write TX (load + Aggregate.expire(clock) + save)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ExpireSentenceFeedbackTx {

    private final SentenceFeedbackRepository repository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SentenceFeedbackId> findExpirableIds(int chunkSize) {
        Instant cutoff = Instant.now(clock);
        return repository.findExpirableSuggestedBefore(cutoff, chunkSize);
    }

    @Transactional
    public void expireOne(SentenceFeedbackId id) {
        SentenceFeedback fb = repository.findById(id)
            .orElseThrow(() -> new SentenceFeedbackNotFoundException(
                "feedback not found during expire batch: " + id.asString()
            ));
        fb.expire(clock);
        repository.save(fb);
    }
}
