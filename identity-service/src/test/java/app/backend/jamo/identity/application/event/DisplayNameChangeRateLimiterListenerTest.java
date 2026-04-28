package app.backend.jamo.identity.application.event;

import app.backend.jamo.identity.domain.event.DisplayNameChanged;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.domain.repository.DisplayNameChangeRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DisplayNameChangeRateLimiterListenerTest {

    private DisplayNameChangeRateLimiter rateLimiter;
    private DisplayNameChangeRateLimiterListener listener;

    @BeforeEach
    void setUp() {
        rateLimiter = mock(DisplayNameChangeRateLimiter.class);
        listener = new DisplayNameChangeRateLimiterListener(rateLimiter);
    }

    @Test
    void handle_calls_markChanged_with_event_userId_and_ttl() {
        UserId userId = UserId.generate();
        Duration ttl = Duration.ofDays(7);

        listener.handle(new DisplayNameChanged(userId, ttl));

        verify(rateLimiter).markChanged(userId, ttl);
    }

    @Test
    void constructor_rejects_null_rateLimiter() {
        try {
            new DisplayNameChangeRateLimiterListener(null);
            org.junit.jupiter.api.Assertions.fail("expected NPE");
        } catch (NullPointerException expected) {
            // ok
        }
        verifyNoInteractions(rateLimiter);
    }
}
