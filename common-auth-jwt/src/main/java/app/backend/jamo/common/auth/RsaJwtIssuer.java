package app.backend.jamo.common.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.util.Date;
import java.util.Objects;

public final class RsaJwtIssuer implements JwtIssuer {

    private final KeyProvider keyProvider;
    private final String issuer;
    private final String audience;

    public RsaJwtIssuer(KeyProvider keyProvider, String issuer, String audience) {
        Objects.requireNonNull(keyProvider, "keyProvider");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(audience, "audience");
        if (issuer.isBlank() || audience.isBlank()) {
            throw new IllegalArgumentException("issuer and audience must not be blank");
        }
        this.keyProvider = keyProvider;
        this.issuer = issuer;
        this.audience = audience;
    }

    @Override
    public String issue(JwtClaims claims) {
        Objects.requireNonNull(claims, "claims");
        try {
            RSAKey key = keyProvider.signingKey();
            JWSSigner signer = new RSASSASigner(key);
            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(claims.subject())
                    .issuer(issuer)
                    .audience(audience)
                    .issueTime(Date.from(claims.issuedAt()))
                    .expirationTime(Date.from(claims.expiresAt()))
                    .claim(JwtClaimNames.SESSION_ID, claims.sessionId())
                    .claim(JwtClaimNames.DEVICE_ID, claims.deviceId())
                    .claim(JwtClaimNames.TOKEN_TYPE, claims.tokenType().name())
                    .build();
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(key.getKeyID())
                    .build();
            SignedJWT jwt = new SignedJWT(header, claimsSet);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to sign JWT", e);
        }
    }
}
