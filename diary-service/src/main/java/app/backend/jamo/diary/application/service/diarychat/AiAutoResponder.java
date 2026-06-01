package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.domain.model.diarychat.ChatMessage;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.model.diarychat.MessageId;
import app.backend.jamo.diary.domain.model.diarychat.MessageSource;
import app.backend.jamo.diary.domain.model.diarychat.MessageText;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.ChatMessageRepository;
import app.backend.jamo.diary.domain.repository.DiaryChatAiGateway;
import app.backend.jamo.diary.domain.repository.DiaryChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * diarychat AI 자동응답 (S4) — ai-enabled 방에 사용자 메시지가 들어오면 chat-service 게이트웨이로 LLM 응답을
 * 생성해 {@code source=AI} 메시지로 저장한다. 롱폴(poll)이 {@code id>after} 로 자동 픽업해 전달.
 *
 * <p>호출 시점: {@link SendMessageService} 의 트랜잭션 <b>커밋 후</b> 비동기(executor). 따라서 본 클래스는
 * 트랜잭션 밖에서 동작 — gRPC 호출(최대 35s)을 DB 트랜잭션 안에 두지 않는다. 단건 메시지 저장은
 * repository(JpaRepository) 자체 트랜잭션에 위임.
 *
 * <p>실패 처리 (S4 결정): FAILED / RATE_LIMITED 는 {@code source=SYSTEM} 안내 메시지를 남겨 사용자가 인지하게
 * 한다. 무한루프 없음 — AI/SYSTEM 메시지는 send 경로를 거치지 않아 재트리거되지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiAutoResponder {

    /** 컨텍스트로 보낼 직전 메시지 최대 건수 (chat-service 가 추가로 자체 상한 적용). */
    static final int CONTEXT_SIZE = 10;

    static final String MSG_FAILED = "AI 응답을 생성하지 못했어요. 잠시 후 다시 시도해 주세요.";
    static final String MSG_RATE_LIMITED = "AI 응답 요청이 너무 많아요. 잠시 후 다시 시도해 주세요.";

    private final DiaryChatRoomRepository roomRepository;
    private final ChatMessageRepository messageRepository;
    private final DiaryChatAiGateway aiGateway;
    private final Clock clock;

    /**
     * @param roomId               대상 방
     * @param senderUserId         사용자 메시지 작성자 (rate limit 키)
     * @param userMessage          응답 대상 메시지 본문
     * @param triggeringMessageId  방금 저장된 사용자 메시지 id — 컨텍스트는 이보다 이전 메시지로 한정
     */
    public void respond(RoomId roomId, UUID senderUserId, String userMessage, long triggeringMessageId) {
        Optional<DiaryChatRoom> roomOpt = roomRepository.findById(roomId);
        if (roomOpt.isEmpty()) {
            return;
        }
        DiaryChatRoom room = roomOpt.get();
        // 커밋~비동기 실행 사이 토글 OFF / 방 삭제 가능 — 재확인 (방어).
        if (room.isDeleted() || !room.aiAssistantEnabled()) {
            return;
        }

        List<DiaryChatAiGateway.RecentMessage> context =
            loadContext(roomId, triggeringMessageId);

        DiaryChatAiGateway.Result result = aiGateway.generate(new DiaryChatAiGateway.Args(
            senderUserId, roomId.value(), userMessage, context, UUID.randomUUID().toString()));

        ChatMessage message = toMessage(roomId, result);
        messageRepository.save(message);
        log.debug("diarychat AI response stored: roomId={} status={} source={}",
            roomId.value(), result.status(), message.source());
    }

    /**
     * 결과 → 저장할 메시지. OK 라도 truncate 후 본문이 공백이면(극단 edge) SYSTEM 안내로 폴백 —
     * MessageText blank 불변식 위반으로 침묵(AI/SYSTEM 둘 다 없음)하는 것을 방지 (code-reviewer H2).
     */
    private ChatMessage toMessage(RoomId roomId, DiaryChatAiGateway.Result result) {
        if (result.status() == DiaryChatAiGateway.Status.OK) {
            String text = truncate(result.assistantMessage());
            if (!text.isBlank()) {
                return ChatMessage.aiMessage(roomId, new MessageText(text), clock);
            }
            log.warn("diarychat AI OK with blank text after truncate — SYSTEM fallback: roomId={}", roomId.value());
            return ChatMessage.systemMessage(roomId, new MessageText(MSG_FAILED), clock);
        }
        String systemText = result.status() == DiaryChatAiGateway.Status.RATE_LIMITED
            ? MSG_RATE_LIMITED
            : MSG_FAILED;
        return ChatMessage.systemMessage(roomId, new MessageText(systemText), clock);
    }

    /** 직전 메시지 컨텍스트 (시간 오래된 순). triggering 메시지 미만만 — 응답 대상은 userMessage 로 별도 전달. */
    private List<DiaryChatAiGateway.RecentMessage> loadContext(RoomId roomId, long triggeringMessageId) {
        List<ChatMessage> recentDesc = messageRepository.findByRoomIdBefore(
            roomId, new MessageId(triggeringMessageId), CONTEXT_SIZE);
        List<DiaryChatAiGateway.RecentMessage> context = new ArrayList<>(recentDesc.size());
        // findByRoomIdBefore 는 내림차순 → 시간순으로 뒤집어 추가.
        for (int i = recentDesc.size() - 1; i >= 0; i--) {
            ChatMessage m = recentDesc.get(i);
            context.add(new DiaryChatAiGateway.RecentMessage(toRole(m.source()), m.text()));
        }
        return context;
    }

    private static String toRole(MessageSource source) {
        return switch (source) {
            case USER -> "user";
            case AI -> "assistant";
            case SYSTEM -> "system";
        };
    }

    /** LLM 응답이 MessageText 한도(code point)를 넘으면 잘라 저장 (생성 메시지로 도메인 불변식 위반 방지). */
    private static String truncate(String text) {
        int codePoints = text.codePointCount(0, text.length());
        if (codePoints <= MessageText.MAX_CODE_POINTS) {
            return text;
        }
        int endIndex = text.offsetByCodePoints(0, MessageText.MAX_CODE_POINTS);
        return text.substring(0, endIndex);
    }
}
