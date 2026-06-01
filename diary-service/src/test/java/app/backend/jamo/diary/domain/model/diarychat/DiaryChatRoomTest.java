package app.backend.jamo.diary.domain.model.diarychat;

import app.backend.jamo.diary.domain.exception.ChatRoomForbiddenException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiaryChatRoomTest {

    private static final UUID DIARY = UUID.randomUUID();
    private static final UUID HOST = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void create_has_null_id_until_persisted() {
        DiaryChatRoom room = DiaryChatRoom.create(DIARY, HOST, true, CLOCK);
        assertThat(room.id()).isNull();   // late identity
        assertThat(room.diaryId()).isEqualTo(DIARY);
        assertThat(room.hostUserId()).isEqualTo(HOST);
        assertThat(room.aiAssistantEnabled()).isTrue();
        assertThat(room.isDeleted()).isFalse();
    }

    @Test
    void isHost_true_only_for_host() {
        DiaryChatRoom room = DiaryChatRoom.create(DIARY, HOST, true, CLOCK);
        assertThat(room.isHost(HOST)).isTrue();
        assertThat(room.isHost(OTHER)).isFalse();
    }

    @Test
    void host_can_toggle_ai_assistant() {
        DiaryChatRoom room = DiaryChatRoom.create(DIARY, HOST, true, CLOCK);
        room.setAiAssistant(HOST, false);
        assertThat(room.aiAssistantEnabled()).isFalse();
    }

    @Test
    void non_host_toggle_rejected_with_forbidden() {
        DiaryChatRoom room = DiaryChatRoom.create(DIARY, HOST, true, CLOCK);
        assertThatThrownBy(() -> room.setAiAssistant(OTHER, false))
            .isInstanceOf(ChatRoomForbiddenException.class);
        assertThat(room.aiAssistantEnabled()).isTrue();  // 변경 안 됨
    }

    @Test
    void toggle_is_idempotent_for_same_value() {
        DiaryChatRoom room = DiaryChatRoom.create(DIARY, HOST, false, CLOCK);
        assertThatCode(() -> {
            room.setAiAssistant(HOST, true);
            room.setAiAssistant(HOST, true);
        }).doesNotThrowAnyException();
        assertThat(room.aiAssistantEnabled()).isTrue();
    }

    @Test
    void markDeleted_sets_deletedAt_idempotently() {
        DiaryChatRoom room = DiaryChatRoom.reconstitute(
            RoomId.of(1), DIARY, HOST, true, CLOCK.instant(), null);
        room.markDeleted(CLOCK);
        assertThat(room.isDeleted()).isTrue();
        Instant first = room.deletedAt();
        room.markDeleted(Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC));
        assertThat(room.deletedAt()).isEqualTo(first);  // 멱등 — 첫 시각 유지
    }
}
