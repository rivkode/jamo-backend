package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.ChatEventView;
import app.backend.jamo.diary.application.dto.diarychat.MessageView;
import app.backend.jamo.diary.application.dto.diarychat.PollView;
import app.backend.jamo.diary.domain.model.diarychat.ChatMessage;
import app.backend.jamo.diary.domain.model.diarychat.ChatRoomEvent;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.ChatMessageRepository;
import app.backend.jamo.diary.domain.repository.ChatRoomEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * GET /api/v1/diary-chatrooms/{roomId}/messages/poll — 롱폴 1회 체크 단위 (orchestration 은
 * presentation 의 DeferredResult coordinator).
 *
 * <p>박제 v2 §8-b: {@link #beginPoll} 가 접근 검증 + baseline(max event_id) 캡처. {@link #pollOnce} 가
 * after 초과 메시지 + baseline 초과 이벤트를 1회 조회. coordinator 가 hasData 까지 반복.
 */
@Service
@RequiredArgsConstructor
public class PollMessagesService {

    /** 단일 poll 응답의 메시지/이벤트 폭주 방지 상한. */
    static final int MAX_BATCH = 200;

    private final ChatRoomAccessGuard accessGuard;
    private final ChatMessageRepository messageRepository;
    private final ChatRoomEventRepository eventRepository;
    private final ChatMessageViewAssembler assembler;

    /** 접근 검증(접근 불가 404) + poll 시작 시점 event baseline 캡처. */
    @Transactional(readOnly = true)
    public long beginPoll(RoomId roomId, UUID requesterUserId) {
        accessGuard.loadAccessibleRoom(roomId, requesterUserId);
        return eventRepository.maxEventIdByRoomId(roomId);
    }

    /** after 초과 메시지 + baseline 초과 이벤트 1회 조회 (hasData 면 coordinator 가 즉시 반환). */
    @Transactional(readOnly = true)
    public PollView pollOnce(RoomId roomId, long after, long baselineEventId) {
        List<ChatMessage> newMessages = messageRepository.findByRoomIdAfter(roomId, after, MAX_BATCH);
        List<MessageView> items = assembler.assemble(newMessages);

        List<ChatRoomEvent> newEvents = eventRepository.findByRoomIdAfter(roomId, baselineEventId, MAX_BATCH);
        List<ChatEventView> events = newEvents.stream()
            .map(e -> new ChatEventView(e.type(), e.createdAt(), e.actorUserId(), e.enabled().orElse(null)))
            .toList();

        long nextAfter = items.isEmpty() ? after : items.get(items.size() - 1).messageId();
        return new PollView(items, events, nextAfter);
    }
}
