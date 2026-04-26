package app.backend.jamo.common.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public final class RsaJwtVerifier implements JwtVerifier {

    private final KeyProvider keyProvider;
    private final String expectedIssuer;
    private final String expectedAudience;
    private final BlacklistChecker blacklistChecker;
    private final Clock clock;
    private final Duration clockSkew;

    public RsaJwtVerifier(KeyProvider keyProvider,
                          String expectedIssuer,
                          String expectedAudience,
                          BlacklistChecker blacklistChecker,
                          Clock clock,
                          Duration clockSkew) {
        Objects.requireNonNull(keyProvider, "keyProvider");
        Objects.requireNonNull(expectedIssuer, "expectedIssuer");
        Objects.requireNonNull(expectedAudience, "expectedAudience");
        Objects.requireNonNull(blacklistChecker, "blacklistChecker");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(clockSkew, "clockSkew");
        if (clockSkew.isNegative()) {
            throw new IllegalArgumentException("clockSkew must be zero or positive");
        }
        this.keyProvider = keyProvider;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.blacklistChecker = blacklistChecker;
        this.clock = clock;
        this.clockSkew = clockSkew;
    }

    @Override
    public JwtClaims verify(String token) {
        Objects.requireNonNull(token, "token");
        SignedJWT jwt = parse(token);
        verifyAlgorithm(jwt);
        RSAKey verificationKey = resolveKey(jwt);
        verifySignature(jwt, verificationKey);
        JWTClaimsSet set = readClaims(jwt);
        verifyExpiration(set);
        verifyIssuer(set);
        verifyAudience(set);

        String sid = stringClaim(set, JwtClaimNames.SESSION_ID);
        if (blacklistChecker.isBlacklisted(sid)) {
            throw new JwtVerificationException("session blacklisted");
        }

        String device = stringClaim(set, JwtClaimNames.DEVICE_ID);
        JwtTokenType tokenType = readTokenType(set);

        return new JwtClaims(
                Objects.requireNonNull(set.getSubject(), "subject claim missing"),
                sid,
                device,
                tokenType,
                set.getIssueTime().toInstant(),
                set.getExpirationTime().toInstant()
        );
    }

    private SignedJWT parse(String token) {
        try {
            return SignedJWT.parse(token);
        } catch (ParseException e) {
            throw new JwtVerificationException("malformed token", e);
        }
    }

    private void verifyAlgorithm(SignedJWT jwt) {
        if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
            throw new JwtVerificationException("unsupported algorithm");
        }
    }

    private RSAKey resolveKey(SignedJWT jwt) {
        String headerKid = jwt.getHeader().getKeyID();
        if (headerKid == null || headerKid.isBlank()) {
            throw new JwtVerificationException("kid header missing");
        }
        RSAKey key = keyProvider.verificationKey();
        if (!headerKid.equals(key.getKeyID())) {
            throw new JwtVerificationException("kid mismatch");
        }
        return key;
    }

    private void verifySignature(SignedJWT jwt, RSAKey verificationKey) {
        try {
            JWSVerifier verifier = new RSASSAVerifier(verificationKey);
            if (!jwt.verify(verifier)) {
                throw new JwtVerificationException("invalid signature");
            }
        } catch (JOSEException e) {
            throw new JwtVerificationException("signature verification failed", e);
        }
    }

    private JWTClaimsSet readClaims(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new JwtVerificationException("claims parse failed", e);
        }
    }

    private void verifyExpiration(JWTClaimsSet set) {
        Date exp = set.getExpirationTime();
        if (exp == null) {
            throw new JwtVerificationException("exp claim missing");
        }
        if (!clock.instant().isBefore(exp.toInstant().plus(clockSkew))) {
            throw new JwtVerificationException("token expired");
        }
    }

    private void verifyIssuer(JWTClaimsSet set) {
        if (!expectedIssuer.equals(set.getIssuer())) {
            throw new JwtVerificationException("issuer mismatch");
        }
    }

    private void verifyAudience(JWTClaimsSet set) {
        List<String> aud = set.getAudience();
        if (aud == null || !aud.contains(expectedAudience)) {
            throw new JwtVerificationException("audience mismatch");
        }
    }

    private String stringClaim(JWTClaimsSet set, String name) {
        Object value = set.getClaim(name);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new JwtVerificationException("missing or invalid claim");
        }
        return s;
    }

    private JwtTokenType readTokenType(JWTClaimsSet set) {
        String typ = stringClaim(set, JwtClaimNames.TOKEN_TYPE);
        try {
            return JwtTokenType.valueOf(typ);
        } catch (IllegalArgumentException e) {
            throw new JwtVerificationException("invalid token type");
        }
    }
}
