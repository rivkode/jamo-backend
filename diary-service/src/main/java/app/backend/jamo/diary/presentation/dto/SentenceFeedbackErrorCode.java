package app.backend.jamo.diary.presentation.dto;

/**
 * sentence-feedback API 응답의 표준 오류 코드.
 *
 * <p>박제: decisions/diary/sentence-feedback-presentation-decisions.md (Q7 그룹화).
 * identity 의 도메인별 ErrorCode 그룹화 패턴 정합 — 향후 diary-service 의 다른 sub-domain
 * (validation / comment / diary core / diarychat) 도 자체 ErrorCode enum.
 */
public enum SentenceFeedbackErrorCode {

    /** 요청 body 검증 실패 (HTTP 400) — Bean Validation, VO 형식 오류, UUID parse 실패. */
    SENTENCE_FEEDBACK_VALIDATION_FAILED,

    /** feedbackId 부재 또는 다른 사용자 소유 (HTTP 404) — IDOR 통일 (박제 §4). */
    SENTENCE_FEEDBACK_NOT_FOUND,

    /** 이미 final 상태에서 accept / reject 재호출 (HTTP 409, 박제 §4). */
    SENTENCE_FEEDBACK_INVALID_TRANSITION,

    /** accept(suggestionId) 가 SUGGESTED 시점 suggestions 에 매칭 X (HTTP 400, 박제 §2). */
    SENTENCE_FEEDBACK_UNKNOWN_SUGGESTION,

    /** 사용자별 분당 / 일일 호출 한도 초과 (HTTP 429, 박제 §11). */
    SENTENCE_FEEDBACK_RATE_LIMITED,

    /** 인증 실패 (HTTP 401) — Bearer 헤더 부재 / JWT 검증 실패 / blacklist sid 등. 구체 사유는 server-side log. */
    UNAUTHORIZED,

    /** 매핑되지 않은 모든 서버 오류 (HTTP 500) — sanitize 책임. */
    INTERNAL_ERROR
}
