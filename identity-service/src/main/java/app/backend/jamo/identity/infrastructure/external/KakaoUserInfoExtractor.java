package app.backend.jamo.identity.infrastructure.external;

import app.backend.jamo.identity.domain.exception.OAuthProviderCallFailedException;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.oauth.OAuthUserInfo;
import app.backend.jamo.identity.domain.model.oauth.ProviderUserId;
import app.backend.jamo.identity.domain.model.user.Email;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class KakaoUserInfoExtractor implements OAuthUserInfoExtractor {

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.KAKAO;
    }

    @Override
    public OAuthUserInfo extract(JsonNode json) {
        JsonNode idNode = json.get("id");
        if (idNode == null || idNode.isNull()) {
            throw new OAuthProviderCallFailedException("kakao userinfo missing 'id'");
        }
        ProviderUserId providerUserId = new ProviderUserId(idNode.asText());

        JsonNode properties = json.get("properties");
        String nickname = (properties != null && properties.hasNonNull("nickname"))
                ? properties.get("nickname").asText()
                : null;
        if (nickname == null || nickname.isBlank()) {
            throw new OAuthProviderCallFailedException("kakao userinfo missing 'properties.nickname'");
        }

        Email email = parseEmail(json);
        return email != null
                ? OAuthUserInfo.of(providerUserId, nickname, email)
                : OAuthUserInfo.withoutEmail(providerUserId, nickname);
    }

    private Email parseEmail(JsonNode json) {
        JsonNode account = json.get("kakao_account");
        if (account == null || !account.hasNonNull("email")) {
            return null;
        }
        try {
            return new Email(account.get("email").asText());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
