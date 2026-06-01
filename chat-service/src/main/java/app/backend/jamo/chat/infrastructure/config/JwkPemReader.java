package app.backend.jamo.chat.infrastructure.config;

import com.nimbusds.jose.jwk.RSAKey;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Verify-only PEM 파서 — chat-service 는 access token 검증만 (private key 미사용). diary-service 정합.
 */
final class JwkPemReader {

    private static final String PUBLIC_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_END = "-----END PUBLIC KEY-----";

    private JwkPemReader() {
    }

    static RSAKey readVerificationKey(String publicKeyPem, String keyId) {
        if (publicKeyPem == null || publicKeyPem.isBlank()) {
            throw new IllegalStateException("JWT public key material is missing. Set JWT_PUBLIC_KEY_PEM.");
        }
        RSAPublicKey publicKey = parsePublicKey(publicKeyPem);
        return new RSAKey.Builder(publicKey).keyID(keyId).build();
    }

    private static RSAPublicKey parsePublicKey(String pem) {
        if (!pem.contains(PUBLIC_BEGIN) || !pem.contains(PUBLIC_END)) {
            throw new IllegalStateException("public key must be X.509 PEM (BEGIN PUBLIC KEY)");
        }
        try {
            byte[] bytes = decodePem(pem);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(bytes);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException("invalid RSA public key PEM", e);
        }
    }

    private static byte[] decodePem(String pem) {
        String stripped = pem.replace(PUBLIC_BEGIN, "").replace(PUBLIC_END, "").replaceAll("\\s+", "");
        return Base64.getDecoder().decode(stripped);
    }
}
