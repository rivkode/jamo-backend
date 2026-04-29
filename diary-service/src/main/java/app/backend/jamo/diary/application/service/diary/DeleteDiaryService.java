package app.backend.jamo.diary.application.service.diary;

import app.backend.jamo.contracts.event.diary.DiaryDeleted;
import app.backend.jamo.diary.application.dto.diary.DeleteDiaryCommand;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import app.backend.jamo.diary.domain.repository.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 일기 삭제 use case.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §9 (작성자 only, 404 통일, hard-delete + DiaryDeleted Saga cascade).
 *
 * <p><b>단일 트랜잭션</b>: Diary hard-delete + Outbox DiaryDeleted insert. 다른 도메인 (comments / diary_likes /
 * sentence_feedback / chatrooms / 랭킹) cascade 는 비동기 구독자 (Saga 패턴, 2PC 미사용).
 *
 * <p>404 통일 (IDOR 보호):
 * <ul>
 *   <li>일기 없음 → 404</li>
 *   <li>비작성자 → 404 (자원 존재 비노출)</li>
 *   <li>이미 삭제 (재호출) → 404 (비멱등)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DeleteDiaryService {

    private final DiaryRepository diaryRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public void delete(DeleteDiaryCommand command) {
        DiaryId diaryId = DiaryId.of(command.diaryId());

        transactionTemplate.executeWithoutResult(status -> {
            Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new DiaryNotFoundException(
                    "diary not found: " + diaryId.asString()));
            if (!diary.isOwnedBy(command.requesterId())) {
                throw new DiaryNotFoundException("diary not found: " + diaryId.asString());
            }
            diaryRepository.deleteById(diaryId);
            outboxEventPublisher.publish(new DiaryDeleted(
                UUID.randomUUID().toString(),
                Instant.now(clock),
                diaryId.asString(),
                diary.authorId().toString()
            ));
        });
    }
}
