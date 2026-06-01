package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.CreateOrGet;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomResult;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.model.diary.DiaryLines;
import app.backend.jamo.diary.domain.model.diary.ImageUrls;
import app.backend.jamo.diary.domain.model.diary.Tags;
import app.backend.jamo.diary.domain.model.diary.Visibility;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.ChatParticipantRepository;
import app.backend.jamo.diary.domain.repository.DiaryChatRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateOrGetChatRoomServiceTest {

    private static final UUID DIARY = UUID.randomUUID();
    private static final UUID REQUESTER = UUID.randomUUID();
    private static final UUID AUTHOR = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);

    private DiaryChatRoomRepository roomRepository;
    private ChatParticipantRepository participantRepository;
    private ChatRoomAccessGuard accessGuard;
    private TransactionTemplate transactionTemplate;
    private CreateOrGetChatRoomService service;

    @BeforeEach
    void setUp() {
        roomRepository = mock(DiaryChatRoomRepository.class);
        participantRepository = mock(ChatParticipantRepository.class);
        accessGuard = mock(ChatRoomAccessGuard.class);
        transactionTemplate = mock(TransactionTemplate.class);
        service = new CreateOrGetChatRoomService(
            roomRepository, participantRepository, accessGuard, transactionTemplate, CLOCK);

        // TransactionTemplate 은 콜백을 그대로 실행 (콜백 내 예외는 그대로 전파 — rollback 시뮬레이션).
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
            ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));

        // 실제 Diary Aggregate (author=AUTHOR, PUBLIC — 누구나 접근). Aggregate Root 는 mock 금지(SKILL §3.2).
        Diary diary = Diary.create(DiaryId.of(DIARY), AUTHOR,
            new DiaryLines(List.of("a", "b", "c")), ImageUrls.empty(), Tags.empty(), Visibility.PUBLIC, CLOCK);
        when(accessGuard.loadAccessibleDiary(DIARY, REQUESTER)).thenReturn(diary);
        when(participantRepository.countByRoomId(any())).thenReturn(0L);
    }

    private DiaryChatRoom persisted(long id) {
        return DiaryChatRoom.reconstitute(RoomId.of(id), DIARY, AUTHOR, true, CLOCK.instant(), null);
    }

    @Test
    void new_room_created_true_with_host_as_author() {
        when(roomRepository.findByDiaryId(DIARY)).thenReturn(Optional.empty());
        when(roomRepository.save(any())).thenReturn(persisted(1));

        ChatRoomResult result = service.createOrGet(new CreateOrGet(DIARY, REQUESTER, true));

        assertThat(result.created()).isTrue();
        assertThat(result.view().roomId()).isEqualTo(1);
        assertThat(result.view().hostUserId()).isEqualTo(AUTHOR);
        verify(roomRepository).save(any());
    }

    @Test
    void existing_room_returned_created_false_without_save() {
        when(roomRepository.findByDiaryId(DIARY)).thenReturn(Optional.of(persisted(7)));

        ChatRoomResult result = service.createOrGet(new CreateOrGet(DIARY, REQUESTER, true));

        assertThat(result.created()).isFalse();
        assertThat(result.view().roomId()).isEqualTo(7);
        verify(roomRepository, never()).save(any());
    }

    @Test
    void race_on_unique_violation_refinds_existing_in_new_transaction() {
        when(roomRepository.findByDiaryId(DIARY))
            .thenReturn(Optional.empty())           // 첫 시도 — 없음
            .thenReturn(Optional.of(persisted(9))); // race 후 새 트랜잭션 재조회 — 승자 방
        when(roomRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup diary_id"));

        ChatRoomResult result = service.createOrGet(new CreateOrGet(DIARY, REQUESTER, true));

        assertThat(result.created()).isFalse();
        assertThat(result.view().roomId()).isEqualTo(9);
        // 생성 시도 + race fallback = execute 2회 (오염 세션 회피).
        verify(transactionTemplate, times(2)).execute(any());
    }
}
