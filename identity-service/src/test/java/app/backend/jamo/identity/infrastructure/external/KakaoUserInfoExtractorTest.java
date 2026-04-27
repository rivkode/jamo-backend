package app.backend.jamo.identity.infrastructure.external;

import app.backend.jamo.identity.domain.exception.OAuthProviderCallFailedException;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.oauth.OAuthUserInfo;
import app.backend.jamo.identity.domain.model.user.Email;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KakaoUserInfoExtractorTest {

    private final KakaoUserInfoExtractor extractor = new KakaoUserInfoExtractor();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void provider_is_kakao() {
        assertThat(extractor.provider()).isEqualTo(OAuthProvider.KAKAO);
    }

    @Test
    void extract_with_full_payload() throws JsonProcessingException {
        JsonNode json = mapper.readTree("""
                {
                  "id": 1234567890,
                  "properties": { "nickname": "jamo-user" },
                  "kakao_account": { "email": "user@kakao.com" }
                }
                """);

        OAuthUserInfo info = extractor.extract(json);

        assertThat(info.providerUserId().value()).isEqualTo("1234567890");
        assertThat(info.rawNickname()).isEqualTo("jamo-user");
        assertThat(info.email()).contains(new Email("user@kakao.com"));
    }

    @Test
    void extract_without_email_when_account_missing() throws JsonProcessingException {
        JsonNode json = mapper.readTree("""
                {
                  "id": 1,
                  "properties": { "nickname": "jamo" }
                }
                """);

        OAuthUserInfo info = extractor.extract(json);

        assertThat(info.email()).isEmpty();
    }

    @Test
    void extract_drops_invalid_email_silently() throws JsonProcessingException {
        JsonNode json = mapper.readTree("""
                {
                  "id": 1,
                  "properties": { "nickname": "jamo" },
                  "kakao_account": { "email": "not-an-email" }
                }
                """);

        OAuthUserInfo info = extractor.extract(json);

        assertThat(info.email()).isEmpty();
    }

    @Test
    void extract_throws_when_id_missing() throws JsonProcessingException {
        JsonNode json = mapper.readTree("""
                { "properties": { "nickname": "jamo" } }
                """);

        assertThatThrownBy(() -> extractor.extract(json))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .hasMessageContaining("id");
    }

    @Test
    void extract_throws_when_nickname_missing() throws JsonProcessingException {
        JsonNode json = mapper.readTree("""
                { "id": 1, "properties": {} }
                """);

        assertThatThrownBy(() -> extractor.extract(json))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .hasMessageContaining("nickname");
    }
}
