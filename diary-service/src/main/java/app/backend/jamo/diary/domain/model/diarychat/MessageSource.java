package app.backend.jamo.diary.domain.model.diarychat;

/**
 * 메시지 출처 (API_SPEC 부록 E.2 source: user|ai|system).
 *
 * <p>USER = 사용자 발화(authorUserId 필수), AI = 어시스턴트 자동응답(S4), SYSTEM = 안내 메시지(S4).
 */
public enum MessageSource {
    USER,
    AI,
    SYSTEM;

    /** API_SPEC 의 소문자 표기. */
    public String wireValue() {
        return name().toLowerCase();
    }
}
