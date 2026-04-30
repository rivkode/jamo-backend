package app.backend.jamo.diary.domain.exception;

/**
 * Comment Aggregate 의 권한 invariant 위반 — 작성자만 가능한 오퍼레이션 (현재 삭제) 호출 시 발생.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §3 (작성자 only) / §4 (404 통일 IDOR 보호).
 *
 * <p><b>Presentation 매핑</b>: HTTP <b>404</b> — 일기 작성자 강제 삭제 권한 미부여 (신고 시스템 후속). 도메인 의미상으로는
 * "권한 거부" 지만 외부 응답은 "자원 없음" 으로 통일 ({@code DiaryAccessDeniedException} 정합).
 */
public class CommentAccessDeniedException extends RuntimeException {
    public CommentAccessDeniedException(String message) {
        super(message);
    }
}
