package app.backend.jamo.diary.domain.exception;

/**
 * ImageUrls VO invariant 위반 — 잘못된 URL scheme / blank / max 5 초과 / control character / 잘못된 host.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §3.
 *
 * <p>Presentation 매핑: HTTP 400.
 *
 * <p><b>도메인 책임 경계</b>: 본 예외는 syntactic 검증 실패만 다룬다. SSRF / IP literal /
 * CDN allow-list 같은 운영 보안은 Infrastructure (이미지 fetch / proxy) 책임 — 본 예외 범위 외.
 */
public class InvalidImageUrlException extends RuntimeException {
    public InvalidImageUrlException(String message) {
        super(message);
    }
}
