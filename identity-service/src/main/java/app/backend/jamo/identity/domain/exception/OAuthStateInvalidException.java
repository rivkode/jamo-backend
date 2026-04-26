package app.backend.jamo.identity.domain.exception;

public class OAuthStateInvalidException extends OAuthAuthenticationException {

    public OAuthStateInvalidException(String message) {
        super(message);
    }
}
