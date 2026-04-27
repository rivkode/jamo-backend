package app.backend.jamo.identity.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Objects;

/**
 * Frontend 의 base URL — OAuth callback success / error redirect 의 destination 호스트.
 *
 * <p>compact constructor 가 fail-fast 검증:
 * <ul>
 *   <li>blank 거부</li>
 *   <li>scheme 이 https 또는 (testing 위해) http 로 시작</li>
 *   <li>유효한 URI 형식</li>
 * </ul>
 *
 * <p>화이트리스트 host 검증은 운영 단계 별도 PR (Open Redirect 추가 방어 — code review M4 후속).
 * 본 record 는 yaml 오타 / 잘못된 prefix 만 fail-fast.
 */
@ConfigurationProperties(prefix = "jamo.frontend")
public record FrontendProperties(String baseUrl) {

    public FrontendProperties {
        Objects.requireNonNull(baseUrl, "baseUrl");
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("frontend.base-url must not be blank");
        }
        if (!baseUrl.startsWith("https://") && !baseUrl.startsWith("http://")) {
            throw new IllegalArgumentException(
                    "frontend.base-url must start with http:// or https:// — got: " + baseUrl);
        }
        try {
            URI.create(baseUrl);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("frontend.base-url is not a valid URI: " + baseUrl, invalid);
        }
    }
}
