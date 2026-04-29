package app.backend.jamo.diary.domain.exception;

/**
 * Diary Aggregate 의 권한 invariant 위반 — 작성자만 가능한 오퍼레이션 (현재 삭제) 호출 시 발생.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §9 (작성자 only).
 *
 * <p><b>Presentation 매핑</b>: HTTP <b>404</b> — IDOR 보호 정책상 403 미사용 (자원 존재 비노출). 도메인 의미상으로는
 * "권한 거부" 지만 외부 응답은 "자원 없음" 으로 통일.
 */
public class DiaryAccessDeniedException extends RuntimeException {
    public DiaryAccessDeniedException(String message) {
        super(message);
    }
}
