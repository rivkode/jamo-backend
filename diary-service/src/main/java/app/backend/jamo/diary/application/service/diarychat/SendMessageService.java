package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.MessageCommands.Send;
import app.backend.jamo.diary.application.dto.diarychat.MessageView;
import app.backend.jamo.diary.domain.model.diarychat.ChatMessage;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.model.diarychat.MessageAudioUrl;
import app.backend.jamo.diary.domain.model.diarychat.MessageText;
import app.backend.jamo.diary.domain.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * POST /api/v1/diary-chatrooms/{roomId}/messages — 사용자 메시지 작성 (source=user).
 *
 * <p>박제 v2 §8-b: text 필수(1..1000), audioUrl optional. STT 는 클라가 처리 — 서버는 텍스트만 받는다.
 * AI 자동응답은 S4. 방 접근은 가드(접근 불가 404).
 */
@Service
@RequiredArgsConstructor
public class SendMessageService {

    private final ChatRoomAccessGuard accessGuard;
    private final ChatMessageRepository messageRepository;
    private final ChatMessageViewAssembler assembler;
    private final Clock clock;

    @Transactional
    public MessageView send(Send command) {
        DiaryChatRoom room = accessGuard.loadAccessibleRoom(command.roomId(), command.senderUserId());
        MessageAudioUrl audioUrl = (command.audioUrl() == null || command.audioUrl().isBlank())
            ? null
            : new MessageAudioUrl(command.audioUrl());
        ChatMessage saved = messageRepository.save(ChatMessage.userMessage(
            room.id(), command.senderUserId(), new MessageText(command.text()), audioUrl, clock));
        return assembler.assembleOne(saved);
    }
}
