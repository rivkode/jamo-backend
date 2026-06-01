package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.domain.exception.ChatRoomNotFoundException;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.DiaryChatRoomRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 채팅방 접근 가드 — 활성 방 로드 + 연결된 일기의 가시성 검증을 한 곳에 모은 collaborator (Application Service
 * 간 직접 호출 회피, 6 서비스 중복 제거).
 *
 * <p>박제 v2 §2: 방 부재 / 삭제 / 비공개 일기 비작성자 → {@link ChatRoomNotFoundException} (404 IDOR 통일).
 * 같은 BC(diary-service)라 {@link DiaryRepository} 직접 read 로 작성자/가시성 확인 (gRPC 불요).
 */
@Component
@RequiredArgsConstructor
public class ChatRoomAccessGuard {

    private final DiaryChatRoomRepository roomRepository;
    private final DiaryRepository diaryRepository;

    /** 활성 방 + 일기 가시성 검증 후 방 반환. 실패는 모두 404 (자원 은닉). */
    public DiaryChatRoom loadAccessibleRoom(RoomId roomId, UUID requesterUserId) {
        DiaryChatRoom room = roomRepository.findById(roomId)
            .filter(r -> !r.isDeleted())
            .orElseThrow(() -> new ChatRoomNotFoundException("chat room not found"));
        assertDiaryAccessible(room.diaryId(), requesterUserId);
        return room;
    }

    /** createOrGet — 방 생성 전 일기 자체의 접근성 검증 (비공개 비작성자 → 404). 반환은 일기(작성자 추출용). */
    public Diary loadAccessibleDiary(UUID diaryId, UUID requesterUserId) {
        Diary diary = diaryRepository.findById(DiaryId.of(diaryId))
            .orElseThrow(() -> new ChatRoomNotFoundException("diary not found"));
        if (!diary.isAccessibleBy(requesterUserId)) {
            throw new ChatRoomNotFoundException("diary not accessible");
        }
        return diary;
    }

    private void assertDiaryAccessible(UUID diaryId, UUID requesterUserId) {
        Diary diary = diaryRepository.findById(DiaryId.of(diaryId))
            .orElseThrow(() -> new ChatRoomNotFoundException("diary not found for room"));
        if (!diary.isAccessibleBy(requesterUserId)) {
            throw new ChatRoomNotFoundException("diary not accessible");
        }
    }
}
