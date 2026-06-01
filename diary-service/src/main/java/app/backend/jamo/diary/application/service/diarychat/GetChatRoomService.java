package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.Get;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomView;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.repository.ChatParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /api/v1/diary-chatrooms/{roomId} — 단건 조회. 접근 불가 → 404 (가드).
 */
@Service
@RequiredArgsConstructor
public class GetChatRoomService {

    private final ChatRoomAccessGuard accessGuard;
    private final ChatParticipantRepository participantRepository;

    @Transactional(readOnly = true)
    public ChatRoomView get(Get query) {
        DiaryChatRoom room = accessGuard.loadAccessibleRoom(query.roomId(), query.requesterUserId());
        return ChatRoomView.of(room, participantRepository.countByRoomId(room.id()));
    }
}
