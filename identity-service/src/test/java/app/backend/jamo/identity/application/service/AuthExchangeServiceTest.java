package app.backend.jamo.identity.application.service;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtIssuer;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.identity.application.dto.AuthExchangeCommand;
import app.backend.jamo.identity.application.dto.AuthExchangeResult;
import app.backend.jamo.identity.domain.exception.AuthCodeExpiredException;
import app.backend.jamo.identity.domain.exception.AuthCodeNotFoundException;
import app.backend.jamo.identity.domain.model.auth.AuthorizationCode;
import app.backend.jamo.identity.domain.model.auth.RefreshTokenRecord;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.domain.repository.AuthorizationCodeStore;
import app.backend.jamo.identity.domain.repository.RefreshTokenStore;
import app.backend.jamo.identity.domain.service.RefreshTokenHasher;
import app.backend.jamo.identity.infrastructure.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthExchangeServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-27T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private AuthorizationCodeStore authCodeStore;
    private JwtIssuer jwtIssuer;
    private RefreshTokenHasher refreshTokenHasher;
    private RefreshTokenStore refreshTokenStore;
    private AuthExchangeService service;

    @BeforeEach
    void setUp() {
        authCodeStore = mock(AuthorizationCodeStore.class);
        jwtIssuer = mock(JwtIssuer.class);
        refreshTokenHasher = mock(RefreshTokenHasher.class);
        refreshTokenStore = mock(RefreshTokenStore.class);

        JwtProperties jwtProps = new JwtProperties(
                "https://issuer", "jamo", "kid-1",
                "private-pem", "public-pem",
                Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofSeconds(30)
        );
        service = new AuthExchangeService(
                authCodeStore, jwtIssuer, refreshTokenHasher, refreshTokenStore, jwtProps, CLOCK);
    }

    private AuthorizationCode validAuthCode(String value) {
        return new AuthorizationCode(
                value, UserId.generate(), "session-1", "device-1",
                NOW.minusSeconds(10), NOW.plusSeconds(50)
        );
    }

    @Test
    void exchange_consumes_authcode_issues_pair_and_stores_refresh_hash() {
        AuthorizationCode code = validAuthCode("authcode-x");
        when(authCodeStore.consume("authcode-x")).thenReturn(Optional.of(code));
        when(jwtIssuer.issue(any(JwtClaims.class)))
                .thenReturn("access-jwt-string", "refresh-jwt-string");
        when(refreshTokenHasher.hash("refresh-jwt-string")).thenReturn("hash-string");

        AuthExchangeResult result = service.exchange(new AuthExchangeCommand("authcode-x"));

        assertThat(result.userId()).isEqualTo(code.userId());
        assertThat(result.accessToken()).isEqualTo("access-jwt-string");
        assertThat(result.refreshToken()).isEqualTo("refresh-jwt-string");
        assertThat(result.expiresInSeconds()).isEqualTo(900L);

        ArgumentCaptor<RefreshTokenRecord> recordCaptor = ArgumentCaptor.forClass(RefreshTokenRecord.class);
        verify(refreshTokenStore).store(recordCaptor.capture());
        RefreshTokenRecord stored = recordCaptor.getValue();
        assertThat(stored.userId()).isEqualTo(code.userId());
        assertThat(stored.sessionId()).isEqualTo("session-1");
        assertThat(stored.deviceId()).isEqualTo("device-1");
        assertThat(stored.tokenHash()).isEqualTo("hash-string");
        assertThat(stored.issuedAt()).isEqualTo(NOW);
        assertThat(stored.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    void exchange_issues_access_then_refresh_with_correct_token_types() {
        AuthorizationCode code = validAuthCode("authcode-x");
        when(authCodeStore.consume("authcode-x")).thenReturn(Optional.of(code));
        when(jwtIssuer.issue(any(JwtClaims.class))).thenReturn("a", "r");
        when(refreshTokenHasher.hash(any())).thenReturn("h");

        service.exchange(new AuthExchangeCommand("authcode-x"));

        ArgumentCaptor<JwtClaims> claimsCaptor = ArgumentCaptor.forClass(JwtClaims.class);
        verify(jwtIssuer, org.mockito.Mockito.times(2)).issue(claimsCaptor.capture());
        var allClaims = claimsCaptor.getAllValues();

        assertThat(allClaims.get(0).tokenType()).isEqualTo(JwtTokenType.ACCESS);
        assertThat(allClaims.get(0).expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(allClaims.get(1).tokenType()).isEqualTo(JwtTokenType.REFRESH);
        assertThat(allClaims.get(1).expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));

        // 두 JWT 모두 동일 sessionId/deviceId/userId
        assertThat(allClaims.get(0).sessionId()).isEqualTo(allClaims.get(1).sessionId())
                .isEqualTo("session-1");
        assertThat(allClaims.get(0).deviceId()).isEqualTo(allClaims.get(1).deviceId())
                .isEqualTo("device-1");
        assertThat(allClaims.get(0).subject()).isEqualTo(allClaims.get(1).subject())
                .isEqualTo(code.userId().asString());
    }

    @Test
    void exchange_throws_not_found_when_authcode_missing() {
        when(authCodeStore.consume("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exchange(new AuthExchangeCommand("missing")))
                .isInstanceOf(AuthCodeNotFoundException.class);

        verify(jwtIssuer, never()).issue(any());
        verify(refreshTokenStore, never()).store(any());
    }

    @Test
    void exchange_throws_expired_when_authcode_past_expiration() {
        // store 가 expired authCode 를 (TTL race 등으로) 반환했을 때 추가 안전판
        AuthorizationCode expired = new AuthorizationCode(
                "x", UserId.generate(), "s", "d",
                NOW.minus(Duration.ofMinutes(5)),
                NOW.minus(Duration.ofMinutes(1))  // already expired
        );
        when(authCodeStore.consume("x")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.exchange(new AuthExchangeCommand("x")))
                .isInstanceOf(AuthCodeExpiredException.class);

        verify(jwtIssuer, never()).issue(any());
        verify(refreshTokenStore, never()).store(any());
    }
}
