package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.Leave;
import app.backend.jamo.diary.domain.model.diarychat.ChatRoomEvent;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.repository.ChatParticipantRepository;
import app.backend.jamo.diary.domain.repository.ChatRoomEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * POST /api/v1/diary-chatrooms/{roomId}/leave — 참여 해제 (멱등, 204).
 *
 * <p>박제 v2 §6/§8-b: participant row 삭제만 — 방 생애주기 무관. 실제 삭제 시에만 롱폴 이벤트 append
 * (비참여자 멱등 호출은 noise 회피). 방 접근 자체는 가드(접근 불가 → 404).
 */
@Service
@RequiredArgsConstructor
public class LeaveChatRoomService {

    private final ChatRoomAccessGuard accessGuard;
    private final ChatParticipantRepository participantRepository;
    private final ChatRoomEventRepository eventRepository;
    private final Clock clock;

    @Transactional
    public void leave(Leave command) {
        DiaryChatRoom room = accessGuard.loadAccessibleRoom(command.roomId(), command.userId());
        int deleted = participantRepository.deleteByRoomIdAndUserId(room.id(), command.userId());
        if (deleted > 0) {
            eventRepository.append(ChatRoomEvent.participantLeft(room.id(), command.userId(), clock));
        }
    }
}
