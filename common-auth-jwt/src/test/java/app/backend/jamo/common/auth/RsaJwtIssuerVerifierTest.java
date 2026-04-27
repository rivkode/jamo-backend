package app.backend.jamo.common.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RsaJwtIssuerVerifierTest {

    private static final String ISSUER = "https://identity.jamoai.app";
    private static final String AUDIENCE = "jamo-services";
    private static final Duration NO_SKEW = Duration.ZERO;
    private static final Instant FIXED_NOW = Instant.parse("2026-04-27T10:00:00Z");

    private static RSAKey signingKey;
    private static RSAKey otherKey;
    private static KeyProvider keyProvider;

    @BeforeAll
    static void generateKeys() throws JOSEException {
        signingKey = new RSAKeyGenerator(2048).keyID("k1").generate();
        otherKey = new RSAKeyGenerator(2048).keyID("k2").generate();
        keyProvider = new RsaKeyPairKeyProvider(signingKey);
    }

    private RsaJwtIssuer issuer() {
        return new RsaJwtIssuer(keyProvider, ISSUER, AUDIENCE);
    }

    private RsaJwtVerifier verifier(BlacklistChecker blacklist, Instant now) {
        return verifier(blacklist, now, NO_SKEW);
    }

    private RsaJwtVerifier verifier(BlacklistChecker blacklist, Instant now, Duration skew) {
        return new RsaJwtVerifier(
                keyProvider, ISSUER, AUDIENCE, blacklist,
                Clock.fixed(now, ZoneOffset.UTC),
                skew
        );
    }

    private JwtClaims sampleClaims(JwtTokenType type) {
        return new JwtClaims("user-1", "sid-abc", "device-x", type, FIXED_NOW, FIXED_NOW.plusSeconds(900));
    }

    @Test
    void issued_token_is_verified_and_returns_same_claims() {
        String token = issuer().issue(sampleClaims(JwtTokenType.ACCESS));

        JwtClaims verified = verifier(BlacklistChecker.noop(), FIXED_NOW.plusSeconds(60)).verify(token);

        assertThat(verified.subject()).isEqualTo("user-1");
        assertThat(verified.sessionId()).isEqualTo("sid-abc");
        assertThat(verified.deviceId()).isEqualTo("device-x");
        assertThat(verified.tokenType()).isEqualTo(JwtTokenType.ACCESS);
    }

    @Test
    void expired_token_is_rejected() {
        String token = issuer().issue(sampleClaims(JwtTokenType.ACCESS));

        assertThatThrownBy(() ->
                verifier(BlacklistChecker.noop(), FIXED_NOW.plusSeconds(901)).verify(token)
        )
                .isInstanceOf(JwtExpiredException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void exact_expiry_with_skew_is_treated_as_expired() {
        String token = issuer().issue(sampleClaims(JwtTokenType.ACCESS));

        assertThatThrownBy(() ->
                verifier(BlacklistChecker.noop(), FIXED_NOW.plusSeconds(930), Duration.ofSeconds(30)).verify(token)
        )
                .isInstanceOf(JwtExpiredException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void clock_skew_extends_expiration_window() {
        String token = issuer().issue(sampleClaims(JwtTokenType.ACCESS));

        assertThat(verifier(BlacklistChecker.noop(), FIXED_NOW.plusSeconds(910), Duration.ofSeconds(30))
                .verify(token).subject()).isEqualTo("user-1");
        assertThatThrownBy(() ->
                verifier(BlacklistChecker.noop(), FIXED_NOW.plusSeconds(931), Duration.ofSeconds(30)).verify(token)
        ).isInstanceOf(JwtVerificationException.class);
    }

    @Test
    void blacklisted_session_is_rejected() {
        String token = issuer().issue(sampleClaims(JwtTokenType.ACCESS));
        BlacklistChecker block = sid -> sid.equals("sid-abc");

        assertThatThrownBy(() -> verifier(block, FIXED_NOW.plusSeconds(60)).verify(token))
                .isInstanceOf(JwtVerificationException.class)
                .hasMessageContaining("blacklisted");
    }

    @Test
    void token_signed_by_unknown_key_is_rejected() throws JOSEException {
        SignedJWT forged = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                validClaims().build()
        );
        forged.sign(new RSASSASigner(otherKey));

        assertThatThrownBy(() ->
                verifier(BlacklistChecker.noop(), FIXED_NOW.plusSeconds(60)).verify(forged.serialize())
        )
                .isInstanceOf(JwtVerificationException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void hs256_token_is_rejected_as_unsupported_algorithm() throws JOSEException {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        SignedJWT hs = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(signingKey.getKeyID()).build(),
                validClaims().build()
        );
        hs.sign(new MACSigner(secret));

        assertThatThrownBy(() ->
                verifier(BlacklistChecker.noop(), FIXED_NOW.plusSeconds(60)).verify(hs.serialize())
        )
                .isInstanceOf(JwtVerificationException.class)
                .hasMessageContaining("algorithm");
    }

    @Test
    void missing_kid_header_is_rejected() throws JOSEException {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                validClaims().build()
        );
        jwt.sign(new RSASSASigner(signingKey));

        assertThatThrownBy(() ->
                verifier(BlacklistChecker.noop(), FIXED_NOW.plusSeconds(60)).verify(jwt.serialize())
        )
                .isInstanceOf(JwtVerificationException.class)
                .hasMessageContaining("kid");
    }

    @Test
    void mismatched_kid_is_rejected() throws JOSEException {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("unknown-kid").build(),
                validClaims().build()
        );
        jwt.sign(new RSASSASigner(signingKey));

        assertThatThrownBy(() ->
                verifier(BlacklistChecker.noop(), FIXED_NOW.plusSeconds(60)).verify(jwt.serialize())
        )
                .isInstanceOf(JwtVerificationException.class)
                .hasMessageContaining("kid");
    }

    @Test
    void issuer_mismatch_is_rejected() throws JOSEException {
        SignedJWT jwt = signWith(validClaims().issuer("https://attacker.example").build());

        assertThatThrownBy(() ->
                verifier(BlacklistChecker.noop(), FIXED_NOW.plusSeconds(60)).verify(jwt.serialize())
        )
                .isInstanceOf(JwtVerificationException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    void audience_mismatch_is_rejected() throws JOSEException {
        SignedJWT jwt = signWith(validClaims().audience("other-aud").build());

        assertThatThrownBy(() ->
                verifier(BlacklistChecker.noop(), FIXED_NOW.plusSeconds(60)).verify(jwt.serialize())
        )
                .isInstanceOf(JwtVerificationException.class)
                .hasMessageContaining("audience");
    }

    @Test
    void missing_sid_claim_is_rejected() throws JOSEException {
        JWTClaimsSet set = new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(Date.from(FIXED_NOW))
                .expirationTime(Date.from(FIXED_NOW.plusSeconds(900)))
                .claim(JwtClaimNames.DEVICE_ID, "device-x")
                .claim(JwtClaimNames.TOKEN_TYPE, "ACCESS")
                .build();
        SignedJWT jwt = signWith(set);

        assertThatThrownBy(() ->
                verifier(BlacklistChecker.noop(), FIXED_NOW.plusSeconds(60)).verify(jwt.serialize())
        )
                .isInstanceOf(JwtVerificationException.class)
                .hasMessageContaining("claim");
    }

    @Test
    void missing_exp_claim_is_rejected() throws JOSEException {
        JWTClaimsSet set = new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(Date.from(FIXED_NOW))
                .claim(JwtClaimNames.SESSION_ID, "sid-abc")
                .claim(JwtClaimNames.DEVICE_ID, "device-x")
                .claim(JwtClaimNames.TOKEN_TYPE, "ACCESS")
                .build();
        SignedJWT jwt = signWith(set);

        assertThatThrownBy(() ->
                verifier(BlacklistChecker.noop(), FIXED_NOW.plusSeconds(60)).verify(jwt.serialize())
        )
                .isInstanceOf(JwtVerificationException.class)
                .hasMessageContaining("exp");
    }

    @Test
    void invalid_token_type_is_rejected() throws JOSEException {
        JWTClaimsSet set = validClaims()
                .claim(JwtClaimNames.TOKEN_TYPE, "BOGUS")
                .build();
        SignedJWT jwt = signWith(set);

        assertThatThrownBy(() ->
                verifier(BlacklistChecker.noop(), FIXED_NOW.plusSeconds(60)).verify(jwt.serialize())
        )
                .isInstanceOf(JwtVerificationException.class)
                .hasMessageContaining("token type");
    }

    @Test
    void malformed_token_is_rejected() {
        assertThatThrownBy(() ->
                verifier(BlacklistChecker.noop(), FIXED_NOW).verify("not-a-jwt")
        )
                .isInstanceOf(JwtVerificationException.class)
                .hasMessageContaining("malformed");
    }

    @Test
    void null_token_is_rejected() {
        assertThatThrownBy(() -> verifier(BlacklistChecker.noop(), FIXED_NOW).verify(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("token");
    }

    @Test
    void refresh_token_round_trip_preserves_type() {
        String token = issuer().issue(sampleClaims(JwtTokenType.REFRESH));

        JwtClaims verified = verifier(BlacklistChecker.noop(), FIXED_NOW.plusSeconds(60)).verify(token);

        assertThat(verified.tokenType()).isEqualTo(JwtTokenType.REFRESH);
    }

    @Test
    void verifier_rejects_negative_clock_skew() {
        assertThatThrownBy(() ->
                new RsaJwtVerifier(keyProvider, ISSUER, AUDIENCE, BlacklistChecker.noop(),
                        Clock.fixed(FIXED_NOW, ZoneOffset.UTC), Duration.ofSeconds(-1))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clockSkew");
    }

    private JWTClaimsSet.Builder validClaims() {
        return new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(Date.from(FIXED_NOW))
                .expirationTime(Date.from(FIXED_NOW.plusSeconds(900)))
                .claim(JwtClaimNames.SESSION_ID, "sid-abc")
                .claim(JwtClaimNames.DEVICE_ID, "device-x")
                .claim(JwtClaimNames.TOKEN_TYPE, "ACCESS");
    }

    private SignedJWT signWith(JWTClaimsSet claimsSet) throws JOSEException {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                claimsSet
        );
        jwt.sign(new RSASSASigner(signingKey));
        return jwt;
    }
}
