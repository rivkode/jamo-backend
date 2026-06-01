package app.backend.jamo.chat.infrastructure.ratelimit;

import app.backend.jamo.chat.infrastructure.config.AiRateLimitProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAiRateLimiterTest {

    private static final String USER = "user-1";

    /** 테스트가 시간을 제어할 수 있는 가변 Clock. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-06-01T10:00:00Z");

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    @Test
    void allows_up_to_max_then_blocks() {
        MutableClock clock = new MutableClock();
        InMemoryAiRateLimiter limiter = new InMemoryAiRateLimiter(
            new AiRateLimitProperties(3, Duration.ofMinutes(1)), clock);

        assertThat(limiter.tryAcquire(USER)).isTrue();
        assertThat(limiter.tryAcquire(USER)).isTrue();
        assertThat(limiter.tryAcquire(USER)).isTrue();
        assertThat(limiter.tryAcquire(USER)).isFalse();  // 4번째 차단
    }

    @Test
    void resets_after_window() {
        MutableClock clock = new MutableClock();
        InMemoryAiRateLimiter limiter = new InMemoryAiRateLimiter(
            new AiRateLimitProperties(1, Duration.ofMinutes(1)), clock);

        assertThat(limiter.tryAcquire(USER)).isTrue();
        assertThat(limiter.tryAcquire(USER)).isFalse();

        clock.advance(Duration.ofSeconds(61));  // 윈도우 경과
        assertThat(limiter.tryAcquire(USER)).isTrue();
    }

    @Test
    void window_boundary_off_by_one() {
        // >= windowMillis 경계 정확성 (test-reviewer M2) — 59s 는 같은 윈도우(차단), 60s 는 리셋(허용).
        MutableClock clock = new MutableClock();
        InMemoryAiRateLimiter limiter = new InMemoryAiRateLimiter(
            new AiRateLimitProperties(1, Duration.ofSeconds(60)), clock);

        assertThat(limiter.tryAcquire(USER)).isTrue();
        clock.advance(Duration.ofSeconds(59));
        assertThat(limiter.tryAcquire(USER)).isFalse();   // 경계 직전 = 같은 윈도우
        clock.advance(Duration.ofSeconds(1));             // 누적 60s == windowMillis
        assertThat(limiter.tryAcquire(USER)).isTrue();    // >= 경계 → 리셋
    }

    @Test
    void per_user_independent() {
        MutableClock clock = new MutableClock();
        InMemoryAiRateLimiter limiter = new InMemoryAiRateLimiter(
            new AiRateLimitProperties(1, Duration.ofMinutes(1)), clock);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        assertThat(limiter.tryAcquire("b")).isTrue();  // 다른 사용자는 독립
    }

    @Test
    void invalid_props_fall_back_to_defaults() {
        AiRateLimitProperties props = new AiRateLimitProperties(0, null);
        assertThat(props.maxPerWindow()).isEqualTo(20);
        assertThat(props.window()).isEqualTo(Duration.ofMinutes(1));
    }
}
