package app.backend.jamo.diary.presentation.dto;

/**
 * comment API 응답의 표준 오류 코드.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §4 (404 통일 IDOR).
 *
 * <p>{@link DiaryErrorCode} 와 의도적으로 분리 — Comment 진입점에서 발생한 오류는 본 enum 으로 응답.
 * Comment service 가 Diary 가드 시 던지는 {@code DiaryNotFoundException} 도 본 Handler 가
 * {@link #COMMENT_NOT_FOUND} 로 매핑 (자원 존재 비노출, 박제 §5).
 */
public enum CommentErrorCode {

    /** 요청 검증 실패 (HTTP 400) — Bean Validation, VO invariant, UUID parse, cursor decode 등. */
    COMMENT_VALIDATION_FAILED,

    /** 댓글/일기 부재, 작성자 아닌 삭제, 비공개 일기 비작성자 (HTTP 404) — IDOR 통일 (박제 §4). */
    COMMENT_NOT_FOUND,

    /** 인증 실패 (HTTP 401) — Bearer 헤더 부재 / JWT 검증 실패 / blacklist sid. */
    UNAUTHORIZED,

    /** 매핑되지 않은 모든 서버 오류 (HTTP 500) — sanitize 책임. */
    INTERNAL_ERROR
}
