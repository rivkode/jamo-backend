package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.MessageCommands.ListMessages;
import app.backend.jamo.diary.application.dto.diarychat.MessageListView;
import app.backend.jamo.diary.application.dto.diarychat.MessageView;
import app.backend.jamo.diary.domain.model.diarychat.ChatMessage;
import app.backend.jamo.diary.domain.model.diarychat.MessageId;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * GET /api/v1/diary-chatrooms/{roomId}/messages — 과거 페이지 (최근 desc, 스크롤 업).
 *
 * <p>박제 v2 §8-b: before(없으면 최신부터), size 기본 30 / 최대 100. limit+1 조회로 hasMore 판단.
 */
@Service
@RequiredArgsConstructor
public class ListMessagesService {

    static final int DEFAULT_SIZE = 30;
    static final int MAX_SIZE = 100;

    private final ChatRoomAccessGuard accessGuard;
    private final ChatMessageRepository messageRepository;
    private final ChatMessageViewAssembler assembler;

    @Transactional(readOnly = true)
    public MessageListView list(ListMessages query) {
        DiaryChatRoom room = accessGuard.loadAccessibleRoom(query.roomId(), query.requesterUserId());
        int size = clampSize(query.size());
        MessageId before = (query.before() == null) ? null : MessageId.of(query.before());

        List<ChatMessage> fetched = messageRepository.findByRoomIdBefore(room.id(), before, size + 1);
        boolean hasMore = fetched.size() > size;
        List<ChatMessage> page = hasMore ? fetched.subList(0, size) : fetched;

        List<MessageView> items = assembler.assemble(page);
        Long oldest = items.isEmpty() ? null : items.get(items.size() - 1).messageId();
        return new MessageListView(items, hasMore, oldest);
    }

    private int clampSize(int requested) {
        if (requested <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(requested, MAX_SIZE);
    }
}
