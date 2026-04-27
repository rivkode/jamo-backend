package app.backend.jamo.identity.infrastructure.external;

import app.backend.jamo.identity.domain.exception.OAuthProviderCallFailedException;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.service.OAuthAuthenticationRequest;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties.ProviderConfig;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties.StateCookieConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 미스config / 누락 분기 검증 (security review H4).
 * WireMock 불필요 — provider 호출에 도달하기 전에 fail.
 */
class HttpOAuthProviderClientConfigurationTest {

    private static final RestClient REST_CLIENT = RestClient.builder().build();
    private static final StateCookieConfig COOKIE = new StateCookieConfig(
            null, false, "Lax", Duration.ofMinutes(5));

    private static OAuthProviderProperties propertiesOnly(OAuthProvider provider) {
        ProviderConfig cfg = new ProviderConfig(
                "client", "secret", "http://test/cb",
                "https://provider/authorize",
                "https://provider/token",
                "https://provider/userinfo",
                "scope", false);
        return new OAuthProviderProperties(
                Duration.ofSeconds(60),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Map.of(provider.name().toLowerCase(java.util.Locale.ROOT), cfg),
                COOKIE);
    }

    @Test
    void throws_when_provider_not_configured_in_properties() {
        // properties 에는 KAKAO 만 등록
        HttpOAuthProviderClient client = new HttpOAuthProviderClient(
                REST_CLIENT,
                propertiesOnly(OAuthProvider.KAKAO),
                List.of(
                        new KakaoUserInfoExtractor(),
                        new GoogleUserInfoExtractor()
                )
        );

        assertThatThrownBy(() -> client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.GOOGLE, "code", "http://test/cb", "v")))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .hasMessageContaining("provider not configured");
    }

    @Test
    void throws_when_no_extractor_registered_for_provider() {
        // properties 에는 KAKAO 가 있지만 extractor 가 없음
        HttpOAuthProviderClient client = new HttpOAuthProviderClient(
                REST_CLIENT,
                propertiesOnly(OAuthProvider.KAKAO),
                List.of()
        );

        assertThatThrownBy(() -> client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.KAKAO, "code", "http://test/cb", null)))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .hasMessageContaining("no extractor registered");
    }
}
