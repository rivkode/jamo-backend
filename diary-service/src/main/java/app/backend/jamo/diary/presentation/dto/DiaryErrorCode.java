package app.backend.jamo.diary.presentation.dto;

/**
 * diary core API 응답의 표준 오류 코드.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §2 (404 통일 IDOR), §9 (작성자 only 404).
 * sentence-feedback {@code SentenceFeedbackErrorCode} 의 도메인별 ErrorCode 그룹화 패턴 정합 — diary-service
 * 의 sub-domain (sentence-feedback / diary core / 향후 comment / diarychat) 마다 자체 ErrorCode enum.
 */
public enum DiaryErrorCode {

    /** 요청 검증 실패 (HTTP 400) — Bean Validation, VO invariant 위반, UUID parse, cursor decode, sort 값,
     *  각 줄 길이 위반 (INVALID_LINE_LENGTH) 등. */
    DIARY_VALIDATION_FAILED,

    /** 일기 줄 개수가 정확히 3 이 아님 (HTTP 422, PRD §2.3 INVALID_LINE_COUNT). 구조적으로 처리 불가한 요청. */
    INVALID_LINE_COUNT,

    /** 한 줄의 길이가 1..200 code points 밖이거나 blank (HTTP 400, PRD §2.3 INVALID_LINE_LENGTH). */
    INVALID_LINE_LENGTH,

    /** diaryId 부재, 비공개+비작성자, 작성자 아닌 삭제 (HTTP 404) — IDOR 통일 (박제 §2). */
    DIARY_NOT_FOUND,

    /** 인증 실패 (HTTP 401) — Bearer 헤더 부재 / JWT 검증 실패 / blacklist sid. 구체 사유는 server-side log. */
    UNAUTHORIZED,

    /** 매핑되지 않은 모든 서버 오류 (HTTP 500) — sanitize 책임. */
    INTERNAL_ERROR
}
