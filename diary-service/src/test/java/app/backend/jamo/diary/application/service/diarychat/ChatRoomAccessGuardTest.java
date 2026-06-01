package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.domain.exception.ChatRoomNotFoundException;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.model.diary.DiaryLines;
import app.backend.jamo.diary.domain.model.diary.ImageUrls;
import app.backend.jamo.diary.domain.model.diary.Tags;
import app.backend.jamo.diary.domain.model.diary.Visibility;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.DiaryChatRoomRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatRoomAccessGuardTest {

    private static final UUID DIARY = UUID.randomUUID();
    private static final UUID HOST = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final RoomId ROOM = RoomId.of(1);

    private DiaryChatRoomRepository roomRepository;
    private DiaryRepository diaryRepository;
    private ChatRoomAccessGuard guard;

    @BeforeEach
    void setUp() {
        roomRepository = mock(DiaryChatRoomRepository.class);
        diaryRepository = mock(DiaryRepository.class);
        guard = new ChatRoomAccessGuard(roomRepository, diaryRepository);
    }

    private DiaryChatRoom activeRoom() {
        return DiaryChatRoom.reconstitute(ROOM, DIARY, HOST, true, Instant.parse("2026-06-01T10:00:00Z"), null);
    }

    // Aggregate Root 는 mock 금지 (SKILL §3.2) — 실제 Diary 사용. PUBLIC = 누구나 접근, PRIVATE = author(HOST) 만.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);

    private Diary publicDiary() {
        return Diary.create(DiaryId.newId(), HOST,
            new DiaryLines(List.of("a", "b", "c")), ImageUrls.empty(), Tags.empty(), Visibility.PUBLIC, CLOCK);
    }

    private Diary privateDiary() {
        return Diary.create(DiaryId.newId(), HOST,
            new DiaryLines(List.of("a", "b", "c")), ImageUrls.empty(), Tags.empty(), Visibility.PRIVATE, CLOCK);
    }

    @Test
    void room_absent_throws_not_found() {
        when(roomRepository.findById(ROOM)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> guard.loadAccessibleRoom(ROOM, HOST))
            .isInstanceOf(ChatRoomNotFoundException.class);
    }

    @Test
    void deleted_room_throws_not_found() {
        DiaryChatRoom deleted = DiaryChatRoom.reconstitute(
            ROOM, DIARY, HOST, true, Instant.parse("2026-06-01T10:00:00Z"), Instant.parse("2026-06-02T00:00:00Z"));
        when(roomRepository.findById(ROOM)).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> guard.loadAccessibleRoom(ROOM, HOST))
            .isInstanceOf(ChatRoomNotFoundException.class);
    }

    @Test
    void private_diary_non_author_throws_not_found() {
        when(roomRepository.findById(ROOM)).thenReturn(Optional.of(activeRoom()));
        when(diaryRepository.findById(any(DiaryId.class))).thenReturn(Optional.of(privateDiary()));
        assertThatThrownBy(() -> guard.loadAccessibleRoom(ROOM, OTHER))
            .isInstanceOf(ChatRoomNotFoundException.class);
    }

    @Test
    void accessible_room_returned() {
        when(roomRepository.findById(ROOM)).thenReturn(Optional.of(activeRoom()));
        when(diaryRepository.findById(any(DiaryId.class))).thenReturn(Optional.of(publicDiary()));
        DiaryChatRoom room = guard.loadAccessibleRoom(ROOM, HOST);
        assertThat(room.id()).isEqualTo(ROOM);
    }

    @Test
    void loadAccessibleDiary_returns_author_for_createOrGet() {
        when(diaryRepository.findById(any(DiaryId.class))).thenReturn(Optional.of(publicDiary()));
        Diary diary = guard.loadAccessibleDiary(DIARY, OTHER);
        assertThat(diary.authorId()).isEqualTo(HOST);
    }

    @Test
    void loadAccessibleDiary_private_non_author_404() {
        when(diaryRepository.findById(any(DiaryId.class))).thenReturn(Optional.of(privateDiary()));
        assertThatThrownBy(() -> guard.loadAccessibleDiary(DIARY, OTHER))
            .isInstanceOf(ChatRoomNotFoundException.class);
    }
}
