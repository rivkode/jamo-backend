package app.backend.jamo.identity.infrastructure.config;

import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties.ProviderConfig;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties.StateCookieConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthProviderPropertiesTest {

    private static final StateCookieConfig COOKIE = new StateCookieConfig(
            null, false, "Lax", Duration.ofMinutes(5));

    private static ProviderConfig validConfig(String tokenUrl, String userinfoUrl) {
        return new ProviderConfig(
                "client", "secret", "http://test/cb",
                "https://provider/authorize",
                tokenUrl, userinfoUrl,
                "scope", false);
    }

    private static OAuthProviderProperties props(Map<String, ProviderConfig> providers) {
        return new OAuthProviderProperties(
                Duration.ofSeconds(60),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                providers, COOKIE);
    }

    @Test
    void provider_keys_are_normalized_to_lowercase() {
        Map<String, ProviderConfig> raw = new LinkedHashMap<>();
        raw.put("KAKAO", validConfig("https://k/token", "https://k/userinfo"));
        raw.put("Google", validConfig("https://g/token", "https://g/userinfo"));

        OAuthProviderProperties p = props(raw);

        assertThat(p.providers()).containsOnlyKeys("kakao", "google");
    }

    @Test
    void rejects_duplicate_provider_keys_after_lowercase() {
        Map<String, ProviderConfig> raw = new LinkedHashMap<>();
        raw.put("KAKAO", validConfig("https://k/token", "https://k/userinfo"));
        raw.put("kakao", validConfig("https://k2/token", "https://k2/userinfo"));

        assertThatThrownBy(() -> props(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate provider key");
    }

    @Test
    void rejects_unknown_provider_key() {
        Map<String, ProviderConfig> raw = Map.of(
                "apple", validConfig("https://a/token", "https://a/userinfo"));

        assertThatThrownBy(() -> props(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apple");
    }

    @Test
    void provider_config_rejects_non_https_token_url() {
        assertThatThrownBy(() -> new ProviderConfig(
                "c", "s", "http://test/cb",
                "https://provider/authorize",
                "http://malicious/token",
                "https://provider/userinfo",
                "scope", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokenUrl")
                .hasMessageContaining("https");
    }

    @Test
    void provider_config_rejects_non_https_userinfo_url() {
        assertThatThrownBy(() -> new ProviderConfig(
                "c", "s", "http://test/cb",
                "https://provider/authorize",
                "https://provider/token",
                "http://malicious/userinfo",
                "scope", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userinfoUrl");
    }

    @Test
    void provider_config_allows_localhost_for_testing() {
        ProviderConfig cfg = new ProviderConfig(
                "c", "s", "http://test/cb",
                "n/a",
                "http://localhost:8080/token",
                "http://127.0.0.1:8080/userinfo",
                "scope", false);

        assertThat(cfg.tokenUrl()).contains("localhost");
        assertThat(cfg.userinfoUrl()).contains("127.0.0.1");
    }

    @Test
    void provider_config_rejects_blank_token_url() {
        assertThatThrownBy(() -> new ProviderConfig(
                "c", "s", "http://test/cb", "n/a", "", "https://x/userinfo", "scope", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokenUrl");
    }

    @Test
    void rejects_null_or_negative_authcode_ttl() {
        Map<String, ProviderConfig> raw = Map.of("kakao",
                validConfig("https://k/token", "https://k/userinfo"));

        assertThatThrownBy(() -> new OAuthProviderProperties(
                Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(2), raw, COOKIE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authcodeTtl");

        assertThatThrownBy(() -> new OAuthProviderProperties(
                null, Duration.ofSeconds(2), Duration.ofSeconds(2), raw, COOKIE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authcodeTtl");
    }

    @Test
    void rejects_null_or_negative_timeouts() {
        Map<String, ProviderConfig> raw = Map.of("kakao",
                validConfig("https://k/token", "https://k/userinfo"));

        assertThatThrownBy(() -> new OAuthProviderProperties(
                Duration.ofSeconds(60), Duration.ZERO, Duration.ofSeconds(2), raw, COOKIE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectTimeout");

        assertThatThrownBy(() -> new OAuthProviderProperties(
                Duration.ofSeconds(60), Duration.ofSeconds(2), Duration.ofSeconds(-1), raw, COOKIE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("readTimeout");
    }

    @Test
    void rejects_empty_providers() {
        assertThatThrownBy(() -> new OAuthProviderProperties(
                Duration.ofSeconds(60), Duration.ofSeconds(2), Duration.ofSeconds(2),
                Map.of(), COOKIE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providers");
    }

    @Test
    void rejects_null_state_cookie() {
        Map<String, ProviderConfig> raw = Map.of("kakao",
                validConfig("https://k/token", "https://k/userinfo"));

        assertThatThrownBy(() -> new OAuthProviderProperties(
                Duration.ofSeconds(60), Duration.ofSeconds(2), Duration.ofSeconds(2), raw, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("stateCookie");
    }
}
