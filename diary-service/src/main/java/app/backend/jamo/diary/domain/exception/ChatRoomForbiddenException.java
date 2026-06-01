package app.backend.jamo.diary.domain.exception;

/**
 * 참여자지만 행위 권한 없음 — ai-toggle 비호스트. presentation 에서 403.
 *
 * <p>비참여자/비공개(404 IDOR)와 구분: 방을 정당하게 아는 참여자라 자원 은닉(404)이 아닌 권한 부족(403).
 * 박제: decisions/diary/diarychat-domain-policy-v2-apispec-e.md §2 (E2.6 명세 명시).
 */
public class ChatRoomForbiddenException extends RuntimeException {

    public ChatRoomForbiddenException(String message) {
        super(message);
    }
}
