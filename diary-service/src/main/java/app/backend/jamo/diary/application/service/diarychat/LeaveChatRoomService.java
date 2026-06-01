package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.Leave;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.repository.ChatParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /api/v1/diary-chatrooms/{roomId}/leave — 참여 해제 (멱등, 204).
 *
 * <p>박제 v2 §6: participant row 삭제만 — 방 생애주기 무관(참여자 0 이어도 active). 비참여자 호출도 204
 * (멱등). 방 접근 자체는 가드(접근 불가 → 404).
 */
@Service
@RequiredArgsConstructor
public class LeaveChatRoomService {

    private final ChatRoomAccessGuard accessGuard;
    private final ChatParticipantRepository participantRepository;

    @Transactional
    public void leave(Leave command) {
        DiaryChatRoom room = accessGuard.loadAccessibleRoom(command.roomId(), command.userId());
        participantRepository.deleteByRoomIdAndUserId(room.id(), command.userId());
    }
}
