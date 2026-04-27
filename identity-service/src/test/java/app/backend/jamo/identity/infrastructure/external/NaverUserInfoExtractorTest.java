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

class NaverUserInfoExtractorTest {

    private final NaverUserInfoExtractor extractor = new NaverUserInfoExtractor();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void provider_is_naver() {
        assertThat(extractor.provider()).isEqualTo(OAuthProvider.NAVER);
    }

    @Test
    void extract_with_full_payload() throws JsonProcessingException {
        JsonNode json = mapper.readTree("""
                {
                  "resultcode": "00",
                  "message": "success",
                  "response": {
                    "id": "naver-id-xyz",
                    "nickname": "jamo-naver",
                    "email": "user@naver.com"
                  }
                }
                """);

        OAuthUserInfo info = extractor.extract(json);

        assertThat(info.providerUserId().value()).isEqualTo("naver-id-xyz");
        assertThat(info.rawNickname()).isEqualTo("jamo-naver");
        assertThat(info.email()).contains(new Email("user@naver.com"));
    }

    @Test
    void extract_falls_back_to_name_when_nickname_missing() throws JsonProcessingException {
        JsonNode json = mapper.readTree("""
                {
                  "response": { "id": "id1", "name": "real-name" }
                }
                """);

        OAuthUserInfo info = extractor.extract(json);

        assertThat(info.rawNickname()).isEqualTo("real-name");
        assertThat(info.email()).isEmpty();
    }

    @Test
    void extract_throws_when_response_missing() throws JsonProcessingException {
        JsonNode json = mapper.readTree("""
                { "resultcode": "024", "message": "fail" }
                """);

        assertThatThrownBy(() -> extractor.extract(json))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .hasMessageContaining("response");
    }

    @Test
    void extract_throws_when_id_missing() throws JsonProcessingException {
        JsonNode json = mapper.readTree("""
                { "response": { "nickname": "x" } }
                """);

        assertThatThrownBy(() -> extractor.extract(json))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .hasMessageContaining("id");
    }

    @Test
    void extract_throws_when_nickname_and_name_missing() throws JsonProcessingException {
        JsonNode json = mapper.readTree("""
                { "response": { "id": "x" } }
                """);

        assertThatThrownBy(() -> extractor.extract(json))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .hasMessageContaining("nickname/name");
    }

    @Test
    void extract_drops_invalid_email_silently() throws JsonProcessingException {
        JsonNode json = mapper.readTree("""
                { "response": { "id": "x", "nickname": "n", "email": "not-an-email" } }
                """);

        OAuthUserInfo info = extractor.extract(json);

        assertThat(info.email()).isEmpty();
    }
}
