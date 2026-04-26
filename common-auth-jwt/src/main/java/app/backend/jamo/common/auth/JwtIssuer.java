package app.backend.jamo.common.auth;

public interface JwtIssuer {

    String issue(JwtClaims claims);
}
