package app.backend.jamo.diary.application.dto.diarychat;

/**
 * createOrGet 결과 — {@code created} 로 presentation 이 201(신규)/200(기존) 분기.
 */
public record ChatRoomResult(ChatRoomView view, boolean created) {
}
