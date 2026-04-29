package app.backend.jamo.diary.domain.exception;

/**
 * Tag / Tags VO invariant 위반 — 단일 태그 길이 (1..30) / 태그 개수 (max 10) / 중복.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §3 / §6.
 *
 * <p>Presentation 매핑: HTTP 400.
 *
 * <p>이미지 URL 검증 실패는 의미 분리를 위해 별도 {@link InvalidImageUrlException} — 운영 모니터링 /
 * 알림 SLO 분리 + 후속 정책 (image 만 별도 처리) 호환.
 */
public class InvalidTagException extends RuntimeException {
    public InvalidTagException(String message) {
        super(message);
    }
}
