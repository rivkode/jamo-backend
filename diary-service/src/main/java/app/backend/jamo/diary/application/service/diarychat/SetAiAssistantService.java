package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.SetAiAssistant;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomView;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.repository.ChatParticipantRepository;
import app.backend.jamo.diary.domain.repository.DiaryChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /api/v1/diary-chatrooms/{roomId}/ai-toggle — host 만 (비호스트 403).
 *
 * <p>박제 v2 §2: 방 접근은 가드(접근 불가 404), host 검증은 도메인 불변식
 * ({@code DiaryChatRoom.setAiAssistant} → 비호스트 {@code ChatRoomForbiddenException} 403). 멱등.
 */
@Service
@RequiredArgsConstructor
public class SetAiAssistantService {

    private final ChatRoomAccessGuard accessGuard;
    private final DiaryChatRoomRepository roomRepository;
    private final ChatParticipantRepository participantRepository;

    @Transactional
    public ChatRoomView setAiAssistant(SetAiAssistant command) {
        DiaryChatRoom room = accessGuard.loadAccessibleRoom(command.roomId(), command.actorUserId());
        room.setAiAssistant(command.actorUserId(), command.enabled());  // 비호스트 → 403
        DiaryChatRoom saved = roomRepository.save(room);
        return ChatRoomView.of(saved, participantRepository.countByRoomId(saved.id()));
    }
}
