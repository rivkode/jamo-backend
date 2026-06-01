package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.CreateOrGet;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomResult;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomView;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.repository.ChatParticipantRepository;
import app.backend.jamo.diary.domain.repository.DiaryChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * POST /api/v1/diary-chatrooms — 일기당 1방 멱등 createOrGet.
 *
 * <p>박제 v2 §5: 기존 방 → 200(created=false), 신규 → 201(created=true). host = 일기 작성자.
 * 비공개 일기 비작성자 → 404.
 *
 * <p><b>동시 생성 race</b> (code-reviewer Critical / ToggleDiaryLikeService 정합): {@code findByDiaryId}
 * 선조회와 {@code save} 사이에 다른 요청이 {@code unique(diary_id)} 를 점유하면
 * {@link DataIntegrityViolationException} → 해당 트랜잭션 rollback-only. 따라서 메서드 전체에
 * {@code @Transactional} 을 두지 않고 {@link TransactionTemplate} 로 (1) 생성 시도 트랜잭션, (2) 실패 시
 * <b>새 트랜잭션</b>에서 승자 방 re-find 를 분리한다. 오염된 세션에서 fallback 조회를 하면
 * {@code UnexpectedRollbackException} 이 난다.
 */
@Service
@RequiredArgsConstructor
public class CreateOrGetChatRoomService {

    private final DiaryChatRoomRepository roomRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatRoomAccessGuard accessGuard;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ChatRoomResult createOrGet(CreateOrGet command) {
        // 가시성 검증 (비공개 비작성자 → 404) — read, 생성 트랜잭션과 분리.
        Diary diary = accessGuard.loadAccessibleDiary(command.diaryId(), command.requesterUserId());

        try {
            return transactionTemplate.execute(status -> {
                DiaryChatRoom existing = roomRepository.findByDiaryId(command.diaryId()).orElse(null);
                if (existing != null) {
                    return new ChatRoomResult(viewOf(existing), false);
                }
                DiaryChatRoom saved = roomRepository.save(DiaryChatRoom.create(
                    command.diaryId(), diary.authorId(), command.aiAssistantEnabled(), clock));
                return new ChatRoomResult(viewOf(saved), true);
            });
        } catch (DataIntegrityViolationException race) {
            // 다른 요청이 먼저 unique(diary_id) 점유 — 새 트랜잭션에서 승자 방 반환.
            return transactionTemplate.execute(status -> {
                DiaryChatRoom winner = roomRepository.findByDiaryId(command.diaryId())
                    .orElseThrow(() -> race);
                return new ChatRoomResult(viewOf(winner), false);
            });
        }
    }

    private ChatRoomView viewOf(DiaryChatRoom room) {
        return ChatRoomView.of(room, participantRepository.countByRoomId(room.id()));
    }
}
