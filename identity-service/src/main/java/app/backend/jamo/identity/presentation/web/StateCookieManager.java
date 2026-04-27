package app.backend.jamo.identity.presentation.web;

import app.backend.jamo.identity.domain.model.auth.AuthState;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties.StateCookieConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * OAuth state cookie 발급/읽기/삭제 (decisions/auth/cookie-policy.md).
 *
 * <p>Cookie name = {@code oauth_state_<provider lowercase>} — provider 별 분리하여
 * 동시 다중 provider 시도 시 충돌 회피 + PRD auth/start.md 와 정합.
 * <p>속성: HttpOnly + SameSite=Lax + Path=/api/v1/auth/oauth + Max-Age=5분 + Secure(yaml flag).
 */
@Component
public class StateCookieManager {

    private static final String NAME_PREFIX = "oauth_state_";
    private static final String COOKIE_PATH = "/api/v1/auth/oauth";

    private final StateCookieConfig config;

    public StateCookieManager(OAuthProviderProperties properties) {
        this.config = properties.stateCookie();
    }

    public Optional<AuthState> read(HttpServletRequest request, OAuthProvider provider) {
        String name = nameFor(provider);
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value == null || value.isBlank()) {
                    return Optional.empty();
                }
                try {
                    return Optional.of(new AuthState(value));
                } catch (IllegalArgumentException invalid) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    public void set(HttpServletResponse response, OAuthProvider provider, AuthState state) {
        ResponseCookie cookie = baseBuilder(provider, state.value())
                .maxAge(config.maxAge())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clear(HttpServletResponse response, OAuthProvider provider) {
        ResponseCookie cookie = baseBuilder(provider, "")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(OAuthProvider provider, String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(nameFor(provider), value)
                .httpOnly(true)
                .secure(config.secure())
                .sameSite(config.sameSite())
                .path(COOKIE_PATH);
        if (config.domain() != null && !config.domain().isBlank()) {
            builder.domain(config.domain());
        }
        return builder;
    }

    private static String nameFor(OAuthProvider provider) {
        return NAME_PREFIX + provider.name().toLowerCase(Locale.ROOT);
    }
}
