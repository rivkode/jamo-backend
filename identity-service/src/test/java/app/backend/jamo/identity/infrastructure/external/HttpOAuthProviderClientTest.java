package app.backend.jamo.identity.infrastructure.external;

import app.backend.jamo.identity.domain.exception.OAuthProviderCallFailedException;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.oauth.OAuthUserInfo;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.service.OAuthAuthenticationRequest;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties.ProviderConfig;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties.StateCookieConfig;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class HttpOAuthProviderClientTest {

    private HttpOAuthProviderClient client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        String base = wm.getHttpBaseUrl();

        Map<String, ProviderConfig> providers = new LinkedHashMap<>();
        providers.put("kakao", new ProviderConfig(
                "kakao-client", "kakao-secret",
                "http://test/cb/kakao",
                "n/a", base + "/kakao/token", base + "/kakao/userinfo",
                "profile_nickname,account_email",
                false
        ));
        providers.put("naver", new ProviderConfig(
                "naver-client", "naver-secret",
                "http://test/cb/naver",
                "n/a", base + "/naver/token", base + "/naver/userinfo",
                "profile,email",
                false
        ));
        providers.put("google", new ProviderConfig(
                "google-client", "google-secret",
                "http://test/cb/google",
                "n/a", base + "/google/token", base + "/google/userinfo",
                "openid profile email",
                true
        ));

        OAuthProviderProperties properties = new OAuthProviderProperties(
                Duration.ofSeconds(60),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                providers,
                new StateCookieConfig(null, false, "Lax", Duration.ofMinutes(5))
        );

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder().requestFactory(factory).build();

        client = new HttpOAuthProviderClient(
                restClient,
                properties,
                List.of(
                        new KakaoUserInfoExtractor(),
                        new NaverUserInfoExtractor(),
                        new GoogleUserInfoExtractor()
                )
        );
    }

    @Test
    void kakao_happy_path_returns_user_info_and_sends_required_form_fields() {
        stubFor(post("/kakao/token").willReturn(okJson("""
                {"access_token":"kakao-at","token_type":"bearer","expires_in":21599}
                """)));
        stubFor(get("/kakao/userinfo")
                .withHeader("Authorization", equalTo("Bearer kakao-at"))
                .willReturn(okJson("""
                        {"id":1234567890,"properties":{"nickname":"jamo-k"},
                         "kakao_account":{"email":"jamo@kakao.com"}}
                        """)));

        OAuthUserInfo info = client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.KAKAO, "auth-code", "http://test/cb/kakao", null));

        assertThat(info.providerUserId().value()).isEqualTo("1234567890");
        assertThat(info.rawNickname()).isEqualTo("jamo-k");
        assertThat(info.email()).contains(new Email("jamo@kakao.com"));

        // OAuth 2.0 spec 준수 — token request body 계약 검증
        verify(postRequestedFor(urlEqualTo("/kakao/token"))
                .withRequestBody(containing("grant_type=authorization_code"))
                .withRequestBody(containing("client_id=kakao-client"))
                .withRequestBody(containing("client_secret=kakao-secret"))
                .withRequestBody(containing("code=auth-code"))
                .withRequestBody(containing("redirect_uri=http%3A%2F%2Ftest%2Fcb%2Fkakao")));
    }

    @Test
    void naver_happy_path_returns_user_info() {
        stubFor(post("/naver/token").willReturn(okJson("""
                {"access_token":"naver-at"}
                """)));
        stubFor(get("/naver/userinfo")
                .withHeader("Authorization", equalTo("Bearer naver-at"))
                .willReturn(okJson("""
                        {"resultcode":"00","message":"success",
                         "response":{"id":"naver-1","nickname":"jamo-n","email":"jamo@naver.com"}}
                        """)));

        OAuthUserInfo info = client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.NAVER, "auth-code", "http://test/cb/naver", null));

        assertThat(info.providerUserId().value()).isEqualTo("naver-1");
        assertThat(info.rawNickname()).isEqualTo("jamo-n");
        assertThat(info.email()).contains(new Email("jamo@naver.com"));
    }

    @Test
    void google_happy_path_returns_user_info() {
        stubFor(post("/google/token").willReturn(okJson("""
                {"access_token":"google-at"}
                """)));
        stubFor(get("/google/userinfo")
                .withHeader("Authorization", equalTo("Bearer google-at"))
                .willReturn(okJson("""
                        {"sub":"g-001","name":"Jamo G","email":"jamo@gmail.com","email_verified":true}
                        """)));

        OAuthUserInfo info = client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.GOOGLE, "auth-code", "http://test/cb/google", "verifier-x"));

        assertThat(info.providerUserId().value()).isEqualTo("g-001");
        assertThat(info.rawNickname()).isEqualTo("Jamo G");
        assertThat(info.email()).contains(new Email("jamo@gmail.com"));
    }

    @Test
    void google_token_request_includes_code_verifier_when_pkce_enabled() {
        stubFor(post("/google/token").willReturn(okJson("""
                {"access_token":"at"}
                """)));
        stubFor(get("/google/userinfo").willReturn(okJson("""
                {"sub":"g","name":"n"}
                """)));

        client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.GOOGLE, "code", "http://test/cb/google", "verifier-zzz"));

        verify(postRequestedFor(urlEqualTo("/google/token"))
                .withRequestBody(containing("code_verifier=verifier-zzz")));
    }

    @Test
    void kakao_token_request_omits_code_verifier_when_pkce_disabled() {
        stubFor(post("/kakao/token").willReturn(okJson("""
                {"access_token":"at"}
                """)));
        stubFor(get("/kakao/userinfo").willReturn(okJson("""
                {"id":1,"properties":{"nickname":"n"}}
                """)));

        client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.KAKAO, "code", "http://test/cb/kakao", "should-be-ignored"));

        verify(postRequestedFor(urlEqualTo("/kakao/token"))
                .withRequestBody(notMatching("(?s).*code_verifier.*")));
    }

    @Test
    void naver_token_request_omits_code_verifier_when_pkce_disabled() {
        stubFor(post("/naver/token").willReturn(okJson("""
                {"access_token":"at"}
                """)));
        stubFor(get("/naver/userinfo").willReturn(okJson("""
                {"response":{"id":"x","nickname":"n"}}
                """)));

        client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.NAVER, "code", "http://test/cb/naver", "should-be-ignored"));

        verify(postRequestedFor(urlEqualTo("/naver/token"))
                .withRequestBody(notMatching("(?s).*code_verifier.*")));
    }

    @Test
    void google_throws_when_pkce_enabled_but_verifier_missing_without_calling_provider() {
        OAuthAuthenticationRequest req = new OAuthAuthenticationRequest(
                OAuthProvider.GOOGLE, "code", "http://test/cb/google", null);

        assertThatThrownBy(() -> client.authenticate(req))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .hasMessageContaining("verifier missing");
    }

    @Test
    void token_4xx_throws_call_failed() {
        stubFor(post("/kakao/token").willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"invalid_grant\"}")));

        assertThatThrownBy(() -> client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.KAKAO, "bad-code", "http://test/cb/kakao", null)))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .hasMessageContaining("token exchange failed");
    }

    @Test
    void token_5xx_throws_call_failed() {
        stubFor(post("/google/token").willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.GOOGLE, "code", "http://test/cb/google", "v")))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .hasMessageContaining("token exchange failed");
    }

    @Test
    void userinfo_4xx_throws_call_failed() {
        stubFor(post("/naver/token").willReturn(okJson("""
                {"access_token":"at"}
                """)));
        stubFor(get("/naver/userinfo").willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"unauthorized\"}")));

        assertThatThrownBy(() -> client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.NAVER, "code", "http://test/cb/naver", null)))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .hasMessageContaining("userinfo failed");
    }

    @Test
    void token_response_missing_access_token_throws() {
        stubFor(post("/kakao/token").willReturn(okJson("""
                {"token_type":"bearer"}
                """)));

        assertThatThrownBy(() -> client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.KAKAO, "code", "http://test/cb/kakao", null)))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .hasMessageContaining("access_token");
    }

    @Test
    void token_response_with_non_bearer_token_type_throws() {
        // RFC 6749 §5.1 + security review M2 — token_type 변경 회피
        stubFor(post("/kakao/token").willReturn(okJson("""
                {"access_token":"at","token_type":"mac"}
                """)));

        assertThatThrownBy(() -> client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.KAKAO, "code", "http://test/cb/kakao", null)))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .hasMessageContaining("token_type");
    }

    @Test
    void token_io_error_throws_call_failed_with_safe_message() {
        // security review H3 — IO error 분기 검증
        stubFor(post("/kakao/token").willReturn(aResponse().withFault(CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.KAKAO, "code", "http://test/cb/kakao", null)))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .hasMessageContaining("io error");
    }

    @Test
    void error_message_does_not_leak_provider_response_body() {
        // security review H1 — provider 가 4xx 응답 본문에 민감정보를 echo 해도 client exception 에 누출 안 됨
        String sensitiveBody = "{\"error\":\"invalid_grant\",\"error_description\":\"token X9F-INTERNAL-LEAK abc\"}";
        stubFor(post("/kakao/token").willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody(sensitiveBody)));

        assertThatThrownBy(() -> client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.KAKAO, "code", "http://test/cb/kakao", null)))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).doesNotContain("X9F-INTERNAL-LEAK");
                    assertThat(ex.getMessage()).doesNotContain("error_description");
                    assertThat(ex.getCause()).isNull();  // cause 끊어 stack trace 누출도 차단
                });
    }

    // userinfo 분기의 보안 회귀 대칭 (test review 잔여 #1~#4)

    @Test
    void userinfo_5xx_throws_call_failed() {
        stubFor(post("/google/token").willReturn(okJson("""
                {"access_token":"at"}
                """)));
        stubFor(get("/google/userinfo").willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.GOOGLE, "code", "http://test/cb/google", "v")))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .hasMessageContaining("userinfo failed");
    }

    @Test
    void userinfo_io_error_throws_call_failed_with_safe_message() {
        stubFor(post("/kakao/token").willReturn(okJson("""
                {"access_token":"at"}
                """)));
        stubFor(get("/kakao/userinfo").willReturn(aResponse().withFault(CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.KAKAO, "code", "http://test/cb/kakao", null)))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .hasMessageContaining("io error");
    }

    @Test
    void userinfo_response_body_does_not_leak_when_4xx() {
        // userinfo 쪽 H1 대칭 — token 과 동일 sanitize 정책이 userinfo 에서도 작동하는지
        String sensitiveBody = "{\"error\":\"unauthorized\",\"detail\":\"AT_FINGERPRINT_LEAK_42\"}";
        stubFor(post("/naver/token").willReturn(okJson("""
                {"access_token":"at"}
                """)));
        stubFor(get("/naver/userinfo").willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody(sensitiveBody)));

        assertThatThrownBy(() -> client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.NAVER, "code", "http://test/cb/naver", null)))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).doesNotContain("AT_FINGERPRINT_LEAK_42");
                    assertThat(ex.getMessage()).doesNotContain("detail");
                    assertThat(ex.getCause()).isNull();
                });
    }

    @Test
    void userinfo_empty_body_throws_call_failed() {
        stubFor(post("/kakao/token").willReturn(okJson("""
                {"access_token":"at"}
                """)));
        stubFor(get("/kakao/userinfo").willReturn(aResponse().withStatus(204)));

        assertThatThrownBy(() -> client.authenticate(new OAuthAuthenticationRequest(
                OAuthProvider.KAKAO, "code", "http://test/cb/kakao", null)))
                .isInstanceOf(OAuthProviderCallFailedException.class);
    }
}
