package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.MessageView;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import app.backend.jamo.diary.domain.model.diarychat.ChatMessage;
import app.backend.jamo.diary.domain.model.diarychat.MessageSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ChatMessage → MessageView 조립 — author displayName 을 UserSummary BatchGet 으로 N+1 회피 조회.
 * AI 메시지는 displayName="AI 어시스턴트", SYSTEM 메시지는 displayName=null (프론트가 source 로 렌더 분기).
 */
@Component
@RequiredArgsConstructor
public class ChatMessageViewAssembler {

    /** AI 어시스턴트 표시명 (부록 E.2 author.username). */
    static final String AI_DISPLAY_NAME = "AI 어시스턴트";

    private final UserSummaryPort userSummaryPort;

    public List<MessageView> assemble(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        Set<UUID> authorIds = messages.stream()
            .map(m -> m.authorUserId().orElse(null))
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
        Map<UUID, UserSummaryView> summaries = authorIds.isEmpty()
            ? Map.of()
            : userSummaryPort.batchGet(authorIds);

        return messages.stream().map(m -> {
            UUID author = m.authorUserId().orElse(null);
            String displayName;
            if (author != null) {
                displayName = UserSummaryView.displayNameOrUnknown(Optional.ofNullable(summaries.get(author)));
            } else if (m.source() == MessageSource.AI) {
                displayName = AI_DISPLAY_NAME;
            } else {
                displayName = null;  // SYSTEM — 프론트가 source 로 렌더 분기
            }
            return new MessageView(
                m.id().value(),
                m.roomId().value(),
                author,
                displayName,
                m.text(),
                m.audioUrl().orElse(null),
                m.source(),
                m.createdAt());
        }).toList();
    }

    public MessageView assembleOne(ChatMessage message) {
        return assemble(List.of(message)).get(0);
    }
}
