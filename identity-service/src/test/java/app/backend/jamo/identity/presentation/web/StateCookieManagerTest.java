package app.backend.jamo.identity.presentation.web;

import app.backend.jamo.identity.domain.model.auth.AuthState;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties.ProviderConfig;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties.StateCookieConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StateCookieManagerTest {

    private StateCookieManager manager;

    @BeforeEach
    void setUp() {
        OAuthProviderProperties properties = new OAuthProviderProperties(
                Duration.ofSeconds(60),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Map.of("kakao", new ProviderConfig(
                        "c", "s", "https://app/cb",
                        "https://p/auth", "https://p/token", "https://p/userinfo",
                        "scope", false)),
                new StateCookieConfig("jamoai.app", true, "Lax", Duration.ofMinutes(5))
        );
        manager = new StateCookieManager(properties);
    }

    @Test
    void set_writes_cookie_with_provider_specific_name_and_attributes() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthState state = AuthState.random();

        manager.set(response, OAuthProvider.KAKAO, state);

        String header = response.getHeader("Set-Cookie");
        assertThat(header).contains("oauth_state_kakao=" + state.value());
        assertThat(header).contains("HttpOnly");
        assertThat(header).contains("Secure");
        assertThat(header).contains("SameSite=Lax");
        assertThat(header).contains("Path=/api/v1/auth/oauth");
        assertThat(header).contains("Domain=jamoai.app");
        assertThat(header).contains("Max-Age=300");
    }

    @Test
    void set_uses_lowercase_provider_in_cookie_name_for_each_provider() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthState state = AuthState.random();

        manager.set(response, OAuthProvider.GOOGLE, state);

        assertThat(response.getHeader("Set-Cookie")).contains("oauth_state_google=");
    }

    @Test
    void clear_writes_cookie_with_zero_max_age_and_empty_value() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        manager.clear(response, OAuthProvider.KAKAO);

        String header = response.getHeader("Set-Cookie");
        assertThat(header).contains("oauth_state_kakao=");
        assertThat(header).contains("Max-Age=0");
    }

    @Test
    void read_returns_state_when_cookie_present() {
        AuthState state = AuthState.random();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("oauth_state_kakao", state.value()));

        assertThat(manager.read(request, OAuthProvider.KAKAO)).contains(state);
    }

    @Test
    void read_returns_empty_when_no_cookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(manager.read(request, OAuthProvider.KAKAO)).isEmpty();
    }

    @Test
    void read_returns_empty_when_cookie_for_different_provider() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("oauth_state_google", "some-state"));

        assertThat(manager.read(request, OAuthProvider.KAKAO)).isEmpty();
    }

    @Test
    void read_returns_empty_when_cookie_value_is_blank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("oauth_state_kakao", ""));

        assertThat(manager.read(request, OAuthProvider.KAKAO)).isEmpty();
    }

    @Test
    void omits_domain_attribute_when_config_domain_blank() {
        OAuthProviderProperties propsNoDomain = new OAuthProviderProperties(
                Duration.ofSeconds(60),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Map.of("kakao", new ProviderConfig(
                        "c", "s", "https://app/cb", "https://p/auth",
                        "https://p/token", "https://p/userinfo", "scope", false)),
                new StateCookieConfig(null, false, "Lax", Duration.ofMinutes(5))
        );
        StateCookieManager localManager = new StateCookieManager(propsNoDomain);
        MockHttpServletResponse response = new MockHttpServletResponse();

        localManager.set(response, OAuthProvider.KAKAO, AuthState.random());

        assertThat(response.getHeader("Set-Cookie")).doesNotContain("Domain=");
    }
}
