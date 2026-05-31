package app.backend.jamo.diary.application.service.diary;

import app.backend.jamo.contracts.event.diary.DiaryCreated;
import app.backend.jamo.diary.application.dto.diary.CreateDiaryCommand;
import app.backend.jamo.diary.application.dto.diary.DiaryView;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryLines;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.model.diary.ImageUrls;
import app.backend.jamo.diary.domain.model.diary.Tags;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import app.backend.jamo.diary.domain.repository.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 일기 작성 use case.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §3 / §4 / §5 / §11 (DiaryCreated Outbox).
 *
 * <p><b>단일 트랜잭션</b>: Diary save + Outbox DiaryCreated insert. UserSummary gRPC 호출은 트랜잭션 외부
 * (read-only, 응답 조립용). Outbox 비동기 발행은 OutboxPoller (sentence-feedback 정합).
 *
 * <p>흐름:
 * <ol>
 *   <li>VO 생성 (DiaryLines / ImageUrls / Tags) — invariant 위반 시 도메인 예외 (개수 422 / 길이·기타 400 매핑)</li>
 *   <li>트랜잭션 안: Diary.create + save + Outbox DiaryCreated insert</li>
 *   <li>트랜잭션 외부: UserSummary gRPC (단건) — fallback 처리 후 응답 조립</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CreateDiaryService {

    private final DiaryRepository diaryRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final UserSummaryPort userSummaryPort;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public DiaryView create(CreateDiaryCommand command) {
        DiaryLines lines = new DiaryLines(command.lines());
        ImageUrls images = new ImageUrls(command.images());
        Tags tags = Tags.ofStrings(command.tags());

        DiaryId diaryId = DiaryId.newId();
        Diary saved = transactionTemplate.execute(status -> {
            Diary diary = Diary.create(
                diaryId, command.authorId(), lines, images, tags, command.visibility(), clock
            );
            diaryRepository.save(diary);
            outboxEventPublisher.publish(new DiaryCreated(
                UUID.randomUUID().toString(),
                Instant.now(clock),
                diaryId.asString(),
                command.authorId().toString()
            ));
            return diary;
        });

        String displayName = UserSummaryView.displayNameOrUnknown(
            userSummaryPort.get(command.authorId()));

        return DiaryView.from(saved, displayName, false);
    }
}
