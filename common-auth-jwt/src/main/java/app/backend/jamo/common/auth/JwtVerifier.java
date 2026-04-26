package app.backend.jamo.common.auth;

public interface JwtVerifier {

    JwtClaims verify(String token);
}
