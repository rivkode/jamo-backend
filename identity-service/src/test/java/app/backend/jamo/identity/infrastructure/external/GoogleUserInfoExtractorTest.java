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

class GoogleUserInfoExtractorTest {

    private final GoogleUserInfoExtractor extractor = new GoogleUserInfoExtractor();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void provider_is_google() {
        assertThat(extractor.provider()).isEqualTo(OAuthProvider.GOOGLE);
    }

    @Test
    void extract_with_full_payload() throws JsonProcessingException {
        JsonNode json = mapper.readTree("""
                {
                  "sub": "108273645",
                  "name": "Jamo Google",
                  "email": "user@gmail.com",
                  "email_verified": true
                }
                """);

        OAuthUserInfo info = extractor.extract(json);

        assertThat(info.providerUserId().value()).isEqualTo("108273645");
        assertThat(info.rawNickname()).isEqualTo("Jamo Google");
        assertThat(info.email()).contains(new Email("user@gmail.com"));
    }

    @Test
    void extract_throws_when_sub_missing() throws JsonProcessingException {
        JsonNode json = mapper.readTree("""
                { "name": "x", "email": "x@y.com" }
                """);

        assertThatThrownBy(() -> extractor.extract(json))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .hasMessageContaining("sub");
    }

    @Test
    void extract_throws_when_name_missing() throws JsonProcessingException {
        JsonNode json = mapper.readTree("""
                { "sub": "1" }
                """);

        assertThatThrownBy(() -> extractor.extract(json))
                .isInstanceOf(OAuthProviderCallFailedException.class)
                .hasMessageContaining("name");
    }

    @Test
    void extract_drops_invalid_email_silently() throws JsonProcessingException {
        JsonNode json = mapper.readTree("""
                { "sub": "1", "name": "n", "email": "not-an-email" }
                """);

        OAuthUserInfo info = extractor.extract(json);

        assertThat(info.email()).isEmpty();
    }
}
