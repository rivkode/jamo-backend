package app.backend.jamo.diary.domain.model.diarychat;

/**
 * 롱폴 전용 in-room 상태 변화 이벤트 타입 (API_SPEC 부록 E.2 DiaryChatEvent.type).
 */
public enum ChatRoomEventType {
    PARTICIPANT_JOINED,
    PARTICIPANT_LEFT,
    AI_TOGGLE_CHANGED;

    public String wireValue() {
        return name().toLowerCase();
    }
}
