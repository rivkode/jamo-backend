package app.backend.jamo.identity.domain.model.auth;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PkceVerifierTest {

    @Test
    void random_verifier_passes_length_constraints() {
        PkceVerifier verifier = PkceVerifier.random(new SecureRandom());

        assertThat(verifier.value().length()).isBetween(PkceVerifier.MIN_LENGTH, PkceVerifier.MAX_LENGTH);
    }

    @Test
    void challenge_is_deterministic_for_same_verifier() {
        PkceVerifier verifier = new PkceVerifier("a".repeat(43));

        PkceChallenge first = verifier.challenge();
        PkceChallenge second = verifier.challenge();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void challenge_matches_round_trip() {
        PkceVerifier verifier = PkceVerifier.random(new SecureRandom());
        PkceChallenge challenge = verifier.challenge();

        assertThat(challenge.matches(verifier)).isTrue();
    }

    @Test
    void challenge_does_not_match_other_verifier() {
        PkceVerifier v1 = new PkceVerifier("a".repeat(43));
        PkceVerifier v2 = new PkceVerifier("b".repeat(43));

        assertThat(v1.challenge().matches(v2)).isFalse();
    }

    @Test
    void verifier_too_short_is_rejected() {
        assertThatThrownBy(() -> new PkceVerifier("a".repeat(42)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifier_too_long_is_rejected() {
        assertThatThrownBy(() -> new PkceVerifier("a".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
