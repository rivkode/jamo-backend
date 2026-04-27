package app.backend.jamo.identity.infrastructure.external;

import app.backend.jamo.identity.domain.exception.OAuthProviderCallFailedException;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.oauth.OAuthUserInfo;
import app.backend.jamo.identity.domain.service.OAuthAuthenticationRequest;
import app.backend.jamo.identity.domain.service.OAuthProviderClient;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties.ProviderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RestClient 기반 OAuth provider 호출 어댑터 (ADR-0006 결정 5).
 * Token endpoint POST → access_token → userinfo endpoint GET → provider 별 extractor 위임.
 */
@Component
public class HttpOAuthProviderClient implements OAuthProviderClient {

    private static final Logger log = LoggerFactory.getLogger(HttpOAuthProviderClient.class);

    private final RestClient restClient;
    private final OAuthProviderProperties properties;
    private final Map<OAuthProvider, OAuthUserInfoExtractor> extractors;

    public HttpOAuthProviderClient(RestClient oauthRestClient,
                                   OAuthProviderProperties properties,
                                   List<OAuthUserInfoExtractor> extractors) {
        this.restClient = oauthRestClient;
        this.properties = properties;
        this.extractors = extractors.stream()
                .collect(Collectors.toUnmodifiableMap(
                        OAuthUserInfoExtractor::provider, Function.identity()));
    }

    @Override
    public OAuthUserInfo authenticate(OAuthAuthenticationRequest request) {
        ProviderConfig cfg = providerConfig(request.provider());
        OAuthUserInfoExtractor extractor = requireExtractor(request.provider());

        String accessToken = exchangeCodeForToken(request, cfg);
        JsonNode userinfoJson = fetchUserInfo(cfg, accessToken, request.provider());
        return extractor.extract(userinfoJson);
    }

    private ProviderConfig providerConfig(OAuthProvider provider) {
        ProviderConfig cfg = properties.providers().get(provider.name().toLowerCase(Locale.ROOT));
        if (cfg == null) {
            throw new OAuthProviderCallFailedException("provider not configured: " + provider);
        }
        return cfg;
    }

    private OAuthUserInfoExtractor requireExtractor(OAuthProvider provider) {
        OAuthUserInfoExtractor extractor = extractors.get(provider);
        if (extractor == null) {
            throw new OAuthProviderCallFailedException("no extractor registered for provider: " + provider);
        }
        return extractor;
    }

    private String exchangeCodeForToken(OAuthAuthenticationRequest request, ProviderConfig cfg) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", cfg.clientId());
        form.add("client_secret", cfg.clientSecret());
        form.add("code", request.authorizationCode());
        form.add("redirect_uri", request.redirectUri());
        if (cfg.pkceEnabled()) {
            String verifier = request.pkceCodeVerifierOpt()
                    .orElseThrow(() -> new OAuthProviderCallFailedException(
                            "pkce enabled but verifier missing for provider: " + request.provider()));
            form.add("code_verifier", verifier);
        } else if (request.pkceCodeVerifierOpt().isPresent()) {
            // ADR-0006 결정 1 + security review M1: silent no-op 방지
            log.warn("oauth verifier supplied but pkce disabled for provider={} — verifier dropped",
                    request.provider());
        }

        try {
            JsonNode tokenJson = restClient.post()
                    .uri(cfg.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);

            if (tokenJson == null || !tokenJson.hasNonNull("access_token")) {
                throw new OAuthProviderCallFailedException(
                        "provider token response missing 'access_token': " + request.provider());
            }
            // RFC 6749 §5.1 + security review M2: token_type 이 명시되면 bearer 만 허용
            if (tokenJson.hasNonNull("token_type")
                    && !"bearer".equalsIgnoreCase(tokenJson.get("token_type").asText())) {
                throw new OAuthProviderCallFailedException(
                        "unsupported token_type for provider: " + request.provider());
            }
            return tokenJson.get("access_token").asText();
        } catch (RestClientResponseException ex) {
            // security review H1: provider 응답 본문이 cause exception message 로 노출되지 않도록
            // cause 를 끊고 status 만 남김. 디버깅 정보는 log 로 보강.
            log.warn("oauth token exchange failed provider={} status={}",
                    request.provider(), ex.getStatusCode());
            throw new OAuthProviderCallFailedException(
                    "token exchange failed for provider: " + request.provider()
                            + " (status=" + ex.getStatusCode().value() + ")");
        } catch (RestClientException ex) {
            // io / parse / connection reset / read timeout 등 status 없는 모든 client-side 예외
            // cause 끊어 stack frame 의 jackson 타입 / endpoint 등 leak 방지. 디버깅은 log.warn 에서.
            log.warn("oauth token exchange io error provider={}", request.provider(), ex);
            throw new OAuthProviderCallFailedException(
                    "token exchange io error for provider: " + request.provider());
        }
    }

    private JsonNode fetchUserInfo(ProviderConfig cfg, String accessToken, OAuthProvider provider) {
        try {
            JsonNode body = restClient.get()
                    .uri(cfg.userinfoUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new OAuthProviderCallFailedException(
                        "provider userinfo response empty: " + provider);
            }
            return body;
        } catch (RestClientResponseException ex) {
            // security review H1: provider 응답 본문 leak 차단
            log.warn("oauth userinfo failed provider={} status={}", provider, ex.getStatusCode());
            throw new OAuthProviderCallFailedException(
                    "userinfo failed for provider: " + provider
                            + " (status=" + ex.getStatusCode().value() + ")");
        } catch (RestClientException ex) {
            log.warn("oauth userinfo io error provider={}", provider, ex);
            throw new OAuthProviderCallFailedException(
                    "userinfo io error for provider: " + provider);
        }
    }
}
