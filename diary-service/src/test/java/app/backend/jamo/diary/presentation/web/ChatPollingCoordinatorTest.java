package app.backend.jamo.diary.presentation.web;

import app.backend.jamo.diary.application.dto.diarychat.MessageView;
import app.backend.jamo.diary.application.dto.diarychat.PollView;
import app.backend.jamo.diary.application.service.diarychat.PollMessagesService;
import app.backend.jamo.diary.domain.exception.ChatRoomNotFoundException;
import app.backend.jamo.diary.domain.model.diarychat.MessageSource;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.presentation.dto.diarychat.PollResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatPollingCoordinator 분기 단위 검증 (test-reviewer M1) — 스케줄러를 mock 해 타이밍 의존 없이 결정론적으로:
 * 즉시 데이터 → 스케줄 미등록, 데이터 없음 → 스케줄 등록, beginPoll 404 → 전파.
 */
class ChatPollingCoordinatorTest {

    private static final RoomId ROOM = RoomId.of(1);
    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    private PollMessagesService pollService;
    private ScheduledExecutorService scheduler;
    private ChatPollingCoordinator coordinator;

    @BeforeEach
    void setUp() {
        pollService = mock(PollMessagesService.class);
        scheduler = mock(ScheduledExecutorService.class);
        coordinator = new ChatPollingCoordinator(pollService, scheduler);
        when(pollService.beginPoll(ROOM, USER)).thenReturn(0L);
    }

    private PollView withMessage() {
        MessageView m = new MessageView(6, 1, USER, "u", "hi", null, MessageSource.USER, NOW);
        return new PollView(List.of(m), List.of(), 6);
    }

    @Test
    void immediate_data_sets_result_without_scheduling() {
        when(pollService.pollOnce(ROOM, 5L, 0L)).thenReturn(withMessage());

        DeferredResult<PollResponse> dr = coordinator.poll(ROOM, 5L, 25, USER);

        assertThat(dr.hasResult()).isTrue();
        verify(scheduler, never()).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
    }

    @Test
    void no_immediate_data_registers_scheduled_check() {
        when(pollService.pollOnce(ROOM, 5L, 0L)).thenReturn(PollView.empty(5L));
        when(scheduler.scheduleWithFixedDelay(any(), anyLong(), anyLong(), any()))
            .thenReturn(mock(ScheduledFuture.class));

        DeferredResult<PollResponse> dr = coordinator.poll(ROOM, 5L, 25, USER);

        assertThat(dr.hasResult()).isFalse();
        verify(scheduler, times(1)).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
    }

    @Test
    void beginPoll_404_propagates_before_scheduling() {
        when(pollService.beginPoll(ROOM, USER)).thenThrow(new ChatRoomNotFoundException("x"));

        assertThatThrownBy(() -> coordinator.poll(ROOM, 5L, 25, USER))
            .isInstanceOf(ChatRoomNotFoundException.class);
        verify(scheduler, never()).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
    }
}
