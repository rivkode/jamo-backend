package app.backend.jamo.identity.application.service;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtIssuer;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.identity.application.dto.AuthExchangeResult;
import app.backend.jamo.identity.application.dto.AuthLoginCommand;
import app.backend.jamo.identity.domain.exception.LoginInvalidException;
import app.backend.jamo.identity.domain.exception.LoginRateLimitedException;
import app.backend.jamo.identity.domain.model.auth.RefreshTokenRecord;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.oauth.ProviderUserId;
import app.backend.jamo.identity.domain.model.user.DisplayName;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.HashedPassword;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.repository.LoginRateLimiter;
import app.backend.jamo.identity.domain.repository.PasswordEncoder;
import app.backend.jamo.identity.domain.repository.RefreshTokenStore;
import app.backend.jamo.identity.domain.repository.UserRepository;
import app.backend.jamo.identity.domain.service.RefreshTokenHasher;
import app.backend.jamo.identity.domain.service.SessionIdGenerator;
import app.backend.jamo.identity.infrastructure.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthLoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-01T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LoginRateLimiter loginRateLimiter;
    @Mock
    private JwtIssuer jwtIssuer;
    @Mock
    private RefreshTokenHasher refreshTokenHasher;
    @Mock
    private RefreshTokenStore refreshTokenStore;
    @Mock
    private SessionIdGenerator sessionIdGenerator;
    private AuthLoginService service;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProps = new JwtProperties(
                "https://issuer", "jamo", "kid-1",
                "private-pem", "public-pem",
                Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofSeconds(30)
        );
        service = new AuthLoginService(
                userRepository, passwordEncoder, loginRateLimiter, jwtIssuer, refreshTokenHasher,
                refreshTokenStore, sessionIdGenerator, jwtProps, CLOCK);
    }

    @Test
    void login_when_local_credentials_valid_issues_pair_and_stores_refresh_hash() {
        User user = localUser();
        HashedPassword hashedPassword = user.hashedPassword().orElseThrow();
        allowLogin();
        when(userRepository.findLocalAccountByEmail(new Email("user@example.com"))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", hashedPassword)).thenReturn(true);
        when(sessionIdGenerator.newSessionId()).thenReturn("session-1");
        when(jwtIssuer.issue(any(JwtClaims.class))).thenReturn("access-jwt", "refresh-jwt");
        when(refreshTokenHasher.hash("refresh-jwt")).thenReturn("refresh-hash");

        AuthExchangeResult result = service.login(
                new AuthLoginCommand("user@example.com", "plain-password", "device-1234", "127.0.0.1"));

        assertThat(result.userId()).isEqualTo(user.id());
        assertThat(result.accessToken()).isEqualTo("access-jwt");
        assertThat(result.refreshToken()).isEqualTo("refresh-jwt");
        assertThat(result.expiresInSeconds()).isEqualTo(900L);

        ArgumentCaptor<RefreshTokenRecord> recordCaptor = ArgumentCaptor.forClass(RefreshTokenRecord.class);
        verify(refreshTokenStore).store(recordCaptor.capture());
        RefreshTokenRecord stored = recordCaptor.getValue();
        assertThat(stored.userId()).isEqualTo(user.id());
        assertThat(stored.sessionId()).isEqualTo("session-1");
        assertThat(stored.deviceId()).isEqualTo("device-1234");
        assertThat(stored.tokenHash()).isEqualTo("refresh-hash");
        assertThat(stored.issuedAt()).isEqualTo(NOW);
        assertThat(stored.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        verify(loginRateLimiter).reset(new Email("user@example.com"), "127.0.0.1", "device-1234");
    }

    @Test
    void login_issues_access_then_refresh_with_same_session_and_device() {
        User user = localUser();
        allowLogin();
        when(userRepository.findLocalAccountByEmail(new Email("user@example.com"))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", user.hashedPassword().orElseThrow())).thenReturn(true);
        when(sessionIdGenerator.newSessionId()).thenReturn("session-1");
        when(jwtIssuer.issue(any(JwtClaims.class))).thenReturn("access-jwt", "refresh-jwt");
        when(refreshTokenHasher.hash("refresh-jwt")).thenReturn("refresh-hash");

        service.login(new AuthLoginCommand("user@example.com", "plain-password", "device-1234", "127.0.0.1"));

        ArgumentCaptor<JwtClaims> claimsCaptor = ArgumentCaptor.forClass(JwtClaims.class);
        verify(jwtIssuer, org.mockito.Mockito.times(2)).issue(claimsCaptor.capture());
        var claims = claimsCaptor.getAllValues();
        assertThat(claims.get(0).tokenType()).isEqualTo(JwtTokenType.ACCESS);
        assertThat(claims.get(1).tokenType()).isEqualTo(JwtTokenType.REFRESH);
        assertThat(claims.get(0).subject()).isEqualTo(user.id().asString());
        assertThat(claims.get(0).sessionId()).isEqualTo("session-1");
        assertThat(claims.get(0).deviceId()).isEqualTo("device-1234");
        assertThat(claims.get(1).sessionId()).isEqualTo("session-1");
        assertThat(claims.get(1).deviceId()).isEqualTo("device-1234");
    }

    @Test
    void login_when_local_account_missing_throws_login_invalid_after_dummy_password_check() {
        allowLogin();
        when(userRepository.findLocalAccountByEmail(new Email("user@example.com"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(
                new AuthLoginCommand("user@example.com", "plain-password", "device-1234", "127.0.0.1")))
                .isInstanceOf(LoginInvalidException.class);

        verify(passwordEncoder).matchesDummy("plain-password");
        verify(loginRateLimiter).recordFailure(new Email("user@example.com"), "127.0.0.1", "device-1234");
        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtIssuer, never()).issue(any());
        verify(refreshTokenStore, never()).store(any());
    }

    @Test
    void login_when_password_mismatch_throws_login_invalid_without_token_issue() {
        User user = localUser();
        allowLogin();
        when(userRepository.findLocalAccountByEmail(new Email("user@example.com"))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", user.hashedPassword().orElseThrow())).thenReturn(false);

        assertThatThrownBy(() -> service.login(
                new AuthLoginCommand("user@example.com", "wrong-password", "device-1234", "127.0.0.1")))
                .isInstanceOf(LoginInvalidException.class);

        verify(loginRateLimiter).recordFailure(new Email("user@example.com"), "127.0.0.1", "device-1234");
        verify(jwtIssuer, never()).issue(any());
        verify(refreshTokenStore, never()).store(any());
    }

    @Test
    void login_when_repository_returns_oauth_only_user_throws_login_invalid() {
        User oauthUser = User.registerWithOAuth(
                OAuthProvider.GOOGLE,
                new ProviderUserId("google-user-1"),
                new DisplayName("oauth user"),
                new Email("user@example.com"),
                NOW);
        allowLogin();
        when(userRepository.findLocalAccountByEmail(new Email("user@example.com"))).thenReturn(Optional.of(oauthUser));

        assertThatThrownBy(() -> service.login(
                new AuthLoginCommand("user@example.com", "plain-password", "device-1234", "127.0.0.1")))
                .isInstanceOf(LoginInvalidException.class);

        verify(passwordEncoder).matchesDummy("plain-password");
        verify(loginRateLimiter).recordFailure(new Email("user@example.com"), "127.0.0.1", "device-1234");
        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtIssuer, never()).issue(any());
    }

    @Test
    void login_when_rate_limited_throws_without_password_check() {
        when(loginRateLimiter.isAllowed(new Email("user@example.com"), "127.0.0.1", "device-1234"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.login(
                new AuthLoginCommand("user@example.com", "plain-password", "device-1234", "127.0.0.1")))
                .isInstanceOf(LoginRateLimitedException.class);

        verify(userRepository, never()).findLocalAccountByEmail(any());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(passwordEncoder, never()).matchesDummy(any());
        verify(jwtIssuer, never()).issue(any());
    }

    private void allowLogin() {
        when(loginRateLimiter.isAllowed(new Email("user@example.com"), "127.0.0.1", "device-1234"))
                .thenReturn(true);
    }

    private static User localUser() {
        return User.registerLocal(
                new DisplayName("local user"),
                new Email("user@example.com"),
                new HashedPassword("$2a$12$abcdefghijklmnopqrstuu123456789012345678901234567890"),
                NOW.minus(Duration.ofDays(1)));
    }
}
