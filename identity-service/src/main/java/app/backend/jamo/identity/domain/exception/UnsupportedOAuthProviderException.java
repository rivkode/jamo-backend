package app.backend.jamo.identity.domain.exception;

public class UnsupportedOAuthProviderException extends OAuthAuthenticationException {

    public UnsupportedOAuthProviderException(String message) {
        super(message);
    }
}
