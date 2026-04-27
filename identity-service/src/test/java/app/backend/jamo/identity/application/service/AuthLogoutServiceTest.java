package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.AuthLogoutCommand;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.domain.repository.RefreshTokenStore;
import app.backend.jamo.identity.domain.repository.SessionBlacklist;
import app.backend.jamo.identity.infrastructure.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuthLogoutServiceTest {

    private static final UserId USER_ID = UserId.generate();
    private static final String SID = "sid-current";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);
    private static final Duration EXPECTED_BLACKLIST_TTL = ACCESS_TTL.plus(CLOCK_SKEW);

    private SessionBlacklist sessionBlacklist;
    private RefreshTokenStore refreshTokenStore;
    private AuthLogoutService service;

    @BeforeEach
    void setUp() {
        sessionBlacklist = mock(SessionBlacklist.class);
        refreshTokenStore = mock(RefreshTokenStore.class);
        JwtProperties jwtProps = new JwtProperties(
                "https://issuer", "jamo", "kid-1",
                "private-pem", "public-pem",
                ACCESS_TTL, Duration.ofDays(30), CLOCK_SKEW
        );
        service = new AuthLogoutService(sessionBlacklist, refreshTokenStore, jwtProps);
    }

    @Test
    void logout_blacklists_sid_with_blacklist_ttl_then_deletes_refresh_record() {
        InOrder order = inOrder(sessionBlacklist, refreshTokenStore);

        service.logout(new AuthLogoutCommand(USER_ID, SID));

        order.verify(sessionBlacklist).blacklist(eq(SID), eq(EXPECTED_BLACKLIST_TTL));
        order.verify(refreshTokenStore).delete(USER_ID, SID);
    }

    @Test
    void logout_uses_access_ttl_plus_clockskew_not_refresh_ttl() {
        service.logout(new AuthLogoutCommand(USER_ID, SID));

        verify(sessionBlacklist).blacklist(eq(SID), eq(EXPECTED_BLACKLIST_TTL));
        verify(sessionBlacklist, never()).blacklist(eq(SID), eq(Duration.ofDays(30)));
        verify(sessionBlacklist, never()).blacklist(eq(SID), eq(ACCESS_TTL));
    }

    @Test
    void logout_propagates_blacklist_failure_and_does_not_delete_refresh_record() {
        doThrow(new RuntimeException("redis down"))
                .when(sessionBlacklist).blacklist(any(), any());

        assertThatThrownBy(() -> service.logout(new AuthLogoutCommand(USER_ID, SID)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("redis down");

        verify(refreshTokenStore, never()).delete(any(), any());
    }
}
