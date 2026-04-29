package app.backend.jamo.diary.domain.model.diary;

import app.backend.jamo.diary.domain.exception.InvalidImageUrlException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

/**
 * 이미지 URL 컬렉션 VO.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §3 (max 5).
 *
 * <p><b>invariant</b>:
 * <ul>
 *   <li>최대 5개</li>
 *   <li>각 element 는 blank 금지 + control character (0x00..0x1F / 0x7F) 차단 + http/https scheme 만 허용 +
 *       absolute URI + host 존재</li>
 *   <li>빈 리스트 허용 — 이미지 없이 작성 가능</li>
 * </ul>
 *
 * <p>이미지 직접 업로드는 별도 endpoint 후속 (Non-Goals) — 본 VO 는 syntactic 검증만.
 *
 * <p>저장 형식은 {@link String} — {@link java.net.URL} 객체는 equals 시 DNS 조회 부작용 (Effective Java Item 11)
 * 이 있어 도메인 보유 비권장.
 *
 * <p><b>도메인 책임 경계 (defense in depth)</b>: 본 VO 는 syntactic 검증만 — SSRF (loopback / IP literal /
 * cloud metadata 169.254.x), CDN allow-list, 실제 fetch 검증은 Infrastructure (이미지 proxy / 다운로더) 책임.
 */
public record ImageUrls(List<String> values) {

    public static final int MAX_SIZE = 5;

    public ImageUrls {
        Objects.requireNonNull(values, "values");
        if (values.size() > MAX_SIZE) {
            throw new InvalidImageUrlException(
                "images size out of range: max " + MAX_SIZE + ", got " + values.size()
            );
        }
        for (int i = 0; i < values.size(); i++) {
            String url = values.get(i);
            if (url == null) {
                throw new InvalidImageUrlException("image url at index " + i + " is null");
            }
            validateHttpUrl(url, i);
        }
        values = List.copyOf(values);
    }

    public static ImageUrls empty() {
        return new ImageUrls(List.of());
    }

    public int size() {
        return values.size();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    private static void validateHttpUrl(String url, int index) {
        if (url.isBlank()) {
            throw new InvalidImageUrlException("image url at index " + index + " is blank");
        }
        if (url.codePoints().anyMatch(cp -> cp < 0x20 || cp == 0x7F)) {
            throw new InvalidImageUrlException(
                "image url at index " + index + " contains control characters");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new InvalidImageUrlException("invalid image url at index " + index + ": " + url);
        }
        if (!uri.isAbsolute()) {
            throw new InvalidImageUrlException(
                "image url at index " + index + " must be absolute: " + url);
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new InvalidImageUrlException(
                "image url scheme must be http or https at index " + index + ": " + url);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidImageUrlException(
                "image url host required at index " + index + ": " + url);
        }
    }
}
