package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.Join;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomView;
import app.backend.jamo.diary.domain.model.diarychat.ChatParticipant;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.repository.ChatParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * POST /api/v1/diary-chatrooms/{roomId}/join — 참여자 등록 (멱등).
 *
 * <p>박제 v2 §6: 이미 참여 중이면 no-op. AI welcome/응답 트리거는 후속 S4 (본 슬라이스는 참여자 등록만).
 */
@Service
@RequiredArgsConstructor
public class JoinChatRoomService {

    private final ChatRoomAccessGuard accessGuard;
    private final ChatParticipantRepository participantRepository;
    private final Clock clock;

    @Transactional
    public ChatRoomView join(Join command) {
        DiaryChatRoom room = accessGuard.loadAccessibleRoom(command.roomId(), command.userId());
        if (!participantRepository.existsByRoomIdAndUserId(room.id(), command.userId())) {
            participantRepository.save(ChatParticipant.join(room.id(), command.userId(), clock));
        }
        return ChatRoomView.of(room, participantRepository.countByRoomId(room.id()));
    }
}
