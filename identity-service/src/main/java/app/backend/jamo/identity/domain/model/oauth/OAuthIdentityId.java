package app.backend.jamo.identity.domain.model.oauth;

import java.util.Objects;
import java.util.UUID;

public record OAuthIdentityId(UUID value) {

    public OAuthIdentityId {
        Objects.requireNonNull(value, "value");
    }

    public static OAuthIdentityId generate() {
        return new OAuthIdentityId(UUID.randomUUID());
    }
}
