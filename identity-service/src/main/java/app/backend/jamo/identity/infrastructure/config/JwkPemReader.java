package app.backend.jamo.identity.infrastructure.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.util.Base64;

final class JwkPemReader {

    private static final String PUBLIC_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_END = "-----END PUBLIC KEY-----";
    private static final String PRIVATE_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_END = "-----END PRIVATE KEY-----";

    private JwkPemReader() {
    }

    static RSAKey readSigningKey(String privateKeyPem, String publicKeyPem, String keyId)
            throws JOSEException, ParseException {
        if (privateKeyPem == null || privateKeyPem.isBlank()
                || publicKeyPem == null || publicKeyPem.isBlank()) {
            throw new IllegalStateException(
                    "JWT key material is missing. Set JWT_PRIVATE_KEY_PEM and JWT_PUBLIC_KEY_PEM."
            );
        }
        RSAPublicKey publicKey = parsePublicKey(publicKeyPem);
        RSAPrivateKey privateKey = parsePrivateKey(privateKeyPem);
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .build();
    }

    private static RSAPublicKey parsePublicKey(String pem) {
        if (!pem.contains(PUBLIC_BEGIN) || !pem.contains(PUBLIC_END)) {
            throw new IllegalStateException("public key must be X.509 PEM (BEGIN PUBLIC KEY)");
        }
        try {
            byte[] bytes = decodePem(pem, PUBLIC_BEGIN, PUBLIC_END);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(bytes);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException("invalid RSA public key PEM", e);
        }
    }

    private static RSAPrivateKey parsePrivateKey(String pem) {
        if (!pem.contains(PRIVATE_BEGIN) || !pem.contains(PRIVATE_END)) {
            throw new IllegalStateException("private key must be PKCS#8 PEM (BEGIN PRIVATE KEY)");
        }
        try {
            byte[] bytes = decodePem(pem, PRIVATE_BEGIN, PRIVATE_END);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("invalid RSA private key PEM (PKCS#8 expected)", e);
        }
    }

    private static byte[] decodePem(String pem, String begin, String end) {
        String stripped = pem
                .replace(begin, "")
                .replace(end, "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(stripped);
    }
}
