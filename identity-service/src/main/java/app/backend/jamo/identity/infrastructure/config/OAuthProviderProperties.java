package app.backend.jamo.identity.infrastructure.config;

import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@ConfigurationProperties(prefix = "jamo.oauth")
public record OAuthProviderProperties(
        Duration authcodeTtl,
        Map<String, ProviderConfig> providers,
        StateCookieConfig stateCookie
) {

    public OAuthProviderProperties {
        if (authcodeTtl == null || authcodeTtl.isZero() || authcodeTtl.isNegative()) {
            throw new IllegalArgumentException("authcodeTtl must be positive");
        }
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("providers must not be empty");
        }
        for (String key : providers.keySet()) {
            try {
                OAuthProvider.valueOf(key.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown provider key in jamo.oauth.providers: " + key);
            }
        }
        Objects.requireNonNull(stateCookie, "stateCookie");
    }

    public record ProviderConfig(
            String clientId,
            String clientSecret,
            String redirectUri,
            String authorizeUrl,
            String tokenUrl,
            String userinfoUrl,
            String scope
    ) {
    }

    public record StateCookieConfig(
            String domain,
            boolean secure,
            String sameSite,
            Duration maxAge
    ) {
    }
}
