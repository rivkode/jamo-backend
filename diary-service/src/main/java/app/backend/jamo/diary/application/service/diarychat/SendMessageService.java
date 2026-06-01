package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.MessageCommands.Send;
import app.backend.jamo.diary.application.dto.diarychat.MessageView;
import app.backend.jamo.diary.domain.model.diarychat.ChatMessage;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.model.diarychat.MessageAudioUrl;
import app.backend.jamo.diary.domain.model.diarychat.MessageText;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * POST /api/v1/diary-chatrooms/{roomId}/messages — 사용자 메시지 작성 (source=user).
 *
 * <p>박제 v2 §8-b: text 필수(1..1000), audioUrl optional. STT 는 클라가 처리 — 서버는 텍스트만 받는다.
 * 방 접근은 가드(접근 불가 404).
 *
 * <p>AI 자동응답 (S4): ai-enabled 방이면 <b>커밋 후 비동기</b>로 {@link AiAutoResponder} 트리거 — 응답은
 * 즉시 유저 메시지만 반환하고, AI 메시지는 롱폴(poll)로 전달 (부록 E.2 계약). 커밋 후 실행이라 유저 메시지가
 * 확정·가시화된 뒤에만 AI 가 동작하고, gRPC 호출이 send 트랜잭션/요청 스레드를 점유하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SendMessageService {

    private final ChatRoomAccessGuard accessGuard;
    private final ChatMessageRepository messageRepository;
    private final ChatMessageViewAssembler assembler;
    private final AiAutoResponder aiAutoResponder;
    private final Executor aiResponderExecutor;
    private final Clock clock;

    @Transactional
    public MessageView send(Send command) {
        DiaryChatRoom room = accessGuard.loadAccessibleRoom(command.roomId(), command.senderUserId());
        MessageAudioUrl audioUrl = (command.audioUrl() == null || command.audioUrl().isBlank())
            ? null
            : new MessageAudioUrl(command.audioUrl());
        ChatMessage saved = messageRepository.save(ChatMessage.userMessage(
            room.id(), command.senderUserId(), new MessageText(command.text()), audioUrl, clock));

        if (room.aiAssistantEnabled()) {
            scheduleAiResponse(room.id(), command.senderUserId(), command.text(), saved.id().value());
        }
        return assembler.assembleOne(saved);
    }

    /** 커밋 후 executor 에 AI 자동응답 제출 — 롤백 시 미실행, 요청 스레드/트랜잭션 비점유. */
    private void scheduleAiResponse(RoomId roomId, UUID senderUserId, String userMessage, long messageId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    aiResponderExecutor.execute(() -> {
                        try {
                            aiAutoResponder.respond(roomId, senderUserId, userMessage, messageId);
                        } catch (RuntimeException e) {
                            // 비동기 best-effort — 실패해도 사용자 메시지 흐름에 영향 없음. 운영 로그만.
                            log.warn("diarychat AI auto-response failed: roomId={} cause={}",
                                roomId.value(), e.getClass().getSimpleName(), e);
                        }
                    });
                } catch (java.util.concurrent.RejectedExecutionException e) {
                    // executor 포화 — AI 응답 드롭 (요청 스레드 비점유 유지). 사용자 메시지는 이미 커밋됨.
                    log.warn("diarychat AI auto-response rejected (executor saturated): roomId={}",
                        roomId.value());
                }
            }
        });
    }
}
