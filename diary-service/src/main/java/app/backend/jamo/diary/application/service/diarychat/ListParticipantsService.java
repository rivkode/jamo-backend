package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.ListParticipants;
import app.backend.jamo.diary.application.dto.diarychat.ParticipantView;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import app.backend.jamo.diary.domain.model.diarychat.ChatParticipant;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.repository.ChatParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * GET /api/v1/diary-chatrooms/{roomId}/participants — 참여자 목록.
 *
 * <p>isHost 는 {@code room.hostUserId} 파생, displayName 은 UserSummary BatchGet 조립 (N+1 회피,
 * NOT_FOUND/장애 시 "(unknown)"). avatarUrl 은 현 미지원 → presentation 에서 null.
 */
@Service
@RequiredArgsConstructor
public class ListParticipantsService {

    private final ChatRoomAccessGuard accessGuard;
    private final ChatParticipantRepository participantRepository;
    private final UserSummaryPort userSummaryPort;

    @Transactional(readOnly = true)
    public List<ParticipantView> list(ListParticipants query) {
        DiaryChatRoom room = accessGuard.loadAccessibleRoom(query.roomId(), query.requesterUserId());
        List<ChatParticipant> participants = participantRepository.findByRoomIdOrderByJoinedAt(room.id());
        if (participants.isEmpty()) {
            return List.of();
        }

        Set<UUID> userIds = participants.stream()
            .map(ChatParticipant::userId)
            .collect(Collectors.toSet());
        Map<UUID, UserSummaryView> summaries = userSummaryPort.batchGet(userIds);

        return participants.stream()
            .map(p -> new ParticipantView(
                p.userId(),
                UserSummaryView.displayNameOrUnknown(Optional.ofNullable(summaries.get(p.userId()))),
                room.isHost(p.userId()),
                p.joinedAt()))
            .toList();
    }
}
