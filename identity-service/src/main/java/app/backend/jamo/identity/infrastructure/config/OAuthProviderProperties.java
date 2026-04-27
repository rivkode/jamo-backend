package app.backend.jamo.identity.infrastructure.config;

import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@ConfigurationProperties(prefix = "jamo.oauth")
public record OAuthProviderProperties(
        Duration authcodeTtl,
        Duration connectTimeout,
        Duration readTimeout,
        Map<String, ProviderConfig> providers,
        StateCookieConfig stateCookie
) {

    public OAuthProviderProperties {
        if (authcodeTtl == null || authcodeTtl.isZero() || authcodeTtl.isNegative()) {
            throw new IllegalArgumentException("authcodeTtl must be positive");
        }
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException("connectTimeout must be positive");
        }
        if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("readTimeout must be positive");
        }
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("providers must not be empty");
        }
        Map<String, ProviderConfig> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, ProviderConfig> entry : providers.entrySet()) {
            String rawKey = entry.getKey();
            try {
                OAuthProvider.valueOf(rawKey.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown provider key in jamo.oauth.providers: " + rawKey);
            }
            String lower = rawKey.toLowerCase(Locale.ROOT);
            if (normalized.put(lower, entry.getValue()) != null) {
                throw new IllegalArgumentException("duplicate provider key (case-insensitive): " + lower);
            }
        }
        providers = Collections.unmodifiableMap(normalized);
        Objects.requireNonNull(stateCookie, "stateCookie");
    }

    public record ProviderConfig(
            String clientId,
            String clientSecret,
            String redirectUri,
            String authorizeUrl,
            String tokenUrl,
            String userinfoUrl,
            String scope,
            boolean pkceEnabled
    ) {
        public ProviderConfig {
            Objects.requireNonNull(redirectUri, "redirectUri");
            Objects.requireNonNull(authorizeUrl, "authorizeUrl");
            Objects.requireNonNull(tokenUrl, "tokenUrl");
            Objects.requireNonNull(userinfoUrl, "userinfoUrl");
            requireHttpsOrLocalhost(tokenUrl, "tokenUrl");
            requireHttpsOrLocalhost(userinfoUrl, "userinfoUrl");
        }

        private static void requireHttpsOrLocalhost(String url, String name) {
            if (url.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            boolean ok = url.startsWith("https://")
                    || url.startsWith("http://localhost")
                    || url.startsWith("http://127.0.0.1");
            if (!ok) {
                throw new IllegalArgumentException(
                        name + " must use https (or localhost for testing only): " + url);
            }
        }
    }

    public record StateCookieConfig(
            String domain,
            boolean secure,
            String sameSite,
            Duration maxAge
    ) {
    }
}
