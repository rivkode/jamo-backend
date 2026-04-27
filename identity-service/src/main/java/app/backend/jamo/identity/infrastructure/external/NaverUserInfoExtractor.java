package app.backend.jamo.identity.infrastructure.external;

import app.backend.jamo.identity.domain.exception.OAuthProviderCallFailedException;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.oauth.OAuthUserInfo;
import app.backend.jamo.identity.domain.model.oauth.ProviderUserId;
import app.backend.jamo.identity.domain.model.user.Email;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class NaverUserInfoExtractor implements OAuthUserInfoExtractor {

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.NAVER;
    }

    @Override
    public OAuthUserInfo extract(JsonNode json) {
        JsonNode response = json.get("response");
        if (response == null || response.isNull()) {
            throw new OAuthProviderCallFailedException("naver userinfo missing 'response'");
        }

        JsonNode idNode = response.get("id");
        if (idNode == null || idNode.isNull()) {
            throw new OAuthProviderCallFailedException("naver userinfo missing 'response.id'");
        }
        ProviderUserId providerUserId = new ProviderUserId(idNode.asText());

        String nickname = response.hasNonNull("nickname")
                ? response.get("nickname").asText()
                : (response.hasNonNull("name") ? response.get("name").asText() : null);
        if (nickname == null || nickname.isBlank()) {
            throw new OAuthProviderCallFailedException("naver userinfo missing nickname/name");
        }

        Email email = parseEmail(response);
        return email != null
                ? OAuthUserInfo.of(providerUserId, nickname, email)
                : OAuthUserInfo.withoutEmail(providerUserId, nickname);
    }

    private Email parseEmail(JsonNode response) {
        if (!response.hasNonNull("email")) {
            return null;
        }
        try {
            return new Email(response.get("email").asText());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
