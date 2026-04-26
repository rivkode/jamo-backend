package app.backend.jamo.identity.domain.exception;

public class AuthCodeNotFoundException extends OAuthAuthenticationException {

    public AuthCodeNotFoundException(String message) {
        super(message);
    }
}
