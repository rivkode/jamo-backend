package app.backend.jamo.common.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlacklistCheckerTest {

    @Test
    void noop_returns_false_for_any_session() {
        BlacklistChecker checker = BlacklistChecker.noop();

        assertThat(checker.isBlacklisted("any-session")).isFalse();
        assertThat(checker.isBlacklisted("")).isFalse();
    }
}
