package app.backend.jamo.identity.infrastructure.external;

import app.backend.jamo.identity.domain.exception.OAuthProviderCallFailedException;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.oauth.OAuthUserInfo;
import app.backend.jamo.identity.domain.model.oauth.ProviderUserId;
import app.backend.jamo.identity.domain.model.user.Email;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class GoogleUserInfoExtractor implements OAuthUserInfoExtractor {

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.GOOGLE;
    }

    @Override
    public OAuthUserInfo extract(JsonNode json) {
        JsonNode subNode = json.get("sub");
        if (subNode == null || subNode.isNull()) {
            throw new OAuthProviderCallFailedException("google userinfo missing 'sub'");
        }
        ProviderUserId providerUserId = new ProviderUserId(subNode.asText());

        String nickname = json.hasNonNull("name") ? json.get("name").asText() : null;
        if (nickname == null || nickname.isBlank()) {
            throw new OAuthProviderCallFailedException("google userinfo missing 'name'");
        }

        Email email = parseEmail(json);
        return email != null
                ? OAuthUserInfo.of(providerUserId, nickname, email)
                : OAuthUserInfo.withoutEmail(providerUserId, nickname);
    }

    private Email parseEmail(JsonNode json) {
        if (!json.hasNonNull("email")) {
            return null;
        }
        // ADR-0006 후속 항목: email_verified 신뢰 정책. 현재는 raw email 저장.
        try {
            return new Email(json.get("email").asText());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
