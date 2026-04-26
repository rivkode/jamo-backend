package app.backend.jamo.identity.domain.exception;

public class AuthCodeExpiredException extends OAuthAuthenticationException {

    public AuthCodeExpiredException(String message) {
        super(message);
    }
}
