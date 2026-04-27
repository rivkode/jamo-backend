package app.backend.jamo.identity.application.service;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtExpiredException;
import app.backend.jamo.common.auth.JwtIssuer;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.common.auth.JwtVerificationException;
import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.identity.application.dto.AuthExchangeResult;
import app.backend.jamo.identity.application.dto.AuthRefreshCommand;
import app.backend.jamo.identity.domain.exception.RefreshTokenExpiredException;
import app.backend.jamo.identity.domain.exception.RefreshTokenInvalidException;
import app.backend.jamo.identity.domain.exception.RefreshTokenReuseDetectedException;
import app.backend.jamo.identity.domain.model.auth.RefreshTokenRecord;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.domain.repository.RefreshTokenStore;
import app.backend.jamo.identity.domain.repository.SessionBlacklist;
import app.backend.jamo.identity.domain.service.RefreshTokenHasher;
import app.backend.jamo.identity.domain.service.SessionIdGenerator;
import app.backend.jamo.identity.infrastructure.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthRefreshServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-27T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UserId USER_ID = UserId.generate();
    private static final String OLD_SID = "sid-old";
    private static final String NEW_SID = "sid-new";
    private static final String DEVICE_ID = "device-1";
    private static final String REFRESH_JWT = "refresh-jwt-string";
    private static final String NEW_ACCESS = "new-access-jwt";
    private static final String NEW_REFRESH = "new-refresh-jwt";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);
    private static final Duration EXPECTED_BLACKLIST_TTL = ACCESS_TTL.plus(CLOCK_SKEW);

    private JwtVerifier jwtVerifier;
    private JwtIssuer jwtIssuer;
    private RefreshTokenHasher refreshTokenHasher;
    private RefreshTokenStore refreshTokenStore;
    private SessionBlacklist sessionBlacklist;
    private SessionIdGenerator sessionIdGenerator;
    private AuthRefreshService service;

    @BeforeEach
    void setUp() {
        jwtVerifier = mock(JwtVerifier.class);
        jwtIssuer = mock(JwtIssuer.class);
        refreshTokenHasher = mock(RefreshTokenHasher.class);
        refreshTokenStore = mock(RefreshTokenStore.class);
        sessionBlacklist = mock(SessionBlacklist.class);
        sessionIdGenerator = () -> NEW_SID;

        JwtProperties jwtProps = new JwtProperties(
                "https://issuer", "jamo", "kid-1",
                "private-pem", "public-pem",
                ACCESS_TTL, REFRESH_TTL, CLOCK_SKEW
        );
        service = new AuthRefreshService(
                jwtVerifier, jwtIssuer, refreshTokenHasher,
                refreshTokenStore, sessionBlacklist, sessionIdGenerator, jwtProps, CLOCK);
    }

    private JwtClaims refreshClaims(JwtTokenType type) {
        return new JwtClaims(
                USER_ID.asString(), OLD_SID, DEVICE_ID, type,
                NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofDays(29))
        );
    }

    private RefreshTokenRecord storedRecord(String hash) {
        return new RefreshTokenRecord(
                USER_ID, OLD_SID, DEVICE_ID, hash,
                NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofDays(29))
        );
    }

    private void primeHappyPath() {
        when(jwtVerifier.verify(REFRESH_JWT)).thenReturn(refreshClaims(JwtTokenType.REFRESH));
        when(refreshTokenHasher.hash(REFRESH_JWT)).thenReturn("stored-hash");
        when(refreshTokenStore.find(USER_ID, OLD_SID))
                .thenReturn(Optional.of(storedRecord("stored-hash")));
        when(jwtIssuer.issue(any(JwtClaims.class))).thenReturn(NEW_ACCESS, NEW_REFRESH);
        when(refreshTokenHasher.hash(NEW_REFRESH)).thenReturn("new-hash");
    }

    @Test
    void refresh_happy_path_rotates_session_and_revokes_old() {
        primeHappyPath();

        AuthExchangeResult result = service.refresh(new AuthRefreshCommand(REFRESH_JWT));

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.accessToken()).isEqualTo(NEW_ACCESS);
        assertThat(result.refreshToken()).isEqualTo(NEW_REFRESH);
        assertThat(result.expiresInSeconds()).isEqualTo(900L);

        ArgumentCaptor<RefreshTokenRecord> captor = ArgumentCaptor.forClass(RefreshTokenRecord.class);
        verify(refreshTokenStore).store(captor.capture());
        RefreshTokenRecord stored = captor.getValue();
        assertThat(stored.sessionId()).isEqualTo(NEW_SID);
        assertThat(stored.userId()).isEqualTo(USER_ID);
        assertThat(stored.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(stored.tokenHash()).isEqualTo("new-hash");
        assertThat(stored.expiresAt()).isEqualTo(NOW.plus(REFRESH_TTL));

        // 구 sid 폐기 + blacklist 등록 (TTL=accessTtl+clockSkew)
        verify(refreshTokenStore).delete(USER_ID, OLD_SID);
        verify(sessionBlacklist).blacklist(eq(OLD_SID), eq(EXPECTED_BLACKLIST_TTL));
    }

    @Test
    void refresh_rotation_order_is_store_new_then_blacklist_old_then_delete_old() {
        primeHappyPath();
        InOrder order = inOrder(refreshTokenStore, sessionBlacklist);

        service.refresh(new AuthRefreshCommand(REFRESH_JWT));

        // (1) 신규 record store → (2) 구 sid blacklist → (3) 구 record delete
        order.verify(refreshTokenStore).store(any(RefreshTokenRecord.class));
        order.verify(sessionBlacklist).blacklist(eq(OLD_SID), eq(EXPECTED_BLACKLIST_TTL));
        order.verify(refreshTokenStore).delete(USER_ID, OLD_SID);
    }

    @Test
    void refresh_does_not_revoke_old_session_when_store_new_record_fails() {
        primeHappyPath();
        doThrow(new RuntimeException("redis down")).when(refreshTokenStore).store(any());

        assertThatThrownBy(() -> service.refresh(new AuthRefreshCommand(REFRESH_JWT)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("redis down");

        // 신규 store 실패 시 구 sid 는 그대로 유지 → 클라이언트 동일 refresh 로 idempotent 재시도 가능
        verify(refreshTokenStore, never()).delete(USER_ID, OLD_SID);
        verify(sessionBlacklist, never()).blacklist(eq(OLD_SID), any());
    }

    @Test
    void refresh_issues_jwts_with_new_session_id_and_correct_token_types() {
        primeHappyPath();

        service.refresh(new AuthRefreshCommand(REFRESH_JWT));

        ArgumentCaptor<JwtClaims> claims = ArgumentCaptor.forClass(JwtClaims.class);
        verify(jwtIssuer, times(2)).issue(claims.capture());
        var all = claims.getAllValues();
        assertThat(all.get(0).tokenType()).isEqualTo(JwtTokenType.ACCESS);
        assertThat(all.get(1).tokenType()).isEqualTo(JwtTokenType.REFRESH);
        assertThat(all.get(0).sessionId()).isEqualTo(NEW_SID);
        assertThat(all.get(1).sessionId()).isEqualTo(NEW_SID);
        assertThat(all.get(0).deviceId()).isEqualTo(DEVICE_ID);
    }

    @Test
    void refresh_throws_expired_when_jwt_verifier_throws_expired() {
        when(jwtVerifier.verify(REFRESH_JWT)).thenThrow(new JwtExpiredException("token expired"));

        assertThatThrownBy(() -> service.refresh(new AuthRefreshCommand(REFRESH_JWT)))
                .isInstanceOf(RefreshTokenExpiredException.class);

        verify(refreshTokenStore, never()).find(any(), any());
        verify(refreshTokenStore, never()).findAllSessionIds(any());
        verify(jwtIssuer, never()).issue(any());
        verify(sessionBlacklist, never()).blacklist(any(), any());
    }

    @Test
    void refresh_throws_invalid_when_jwt_verifier_throws_other_verification_failure() {
        when(jwtVerifier.verify(REFRESH_JWT))
                .thenThrow(new JwtVerificationException("invalid signature"));

        assertThatThrownBy(() -> service.refresh(new AuthRefreshCommand(REFRESH_JWT)))
                .isInstanceOf(RefreshTokenInvalidException.class);

        verify(refreshTokenStore, never()).findAllSessionIds(any());
        verify(sessionBlacklist, never()).blacklist(any(), any());
    }

    @Test
    void refresh_throws_invalid_when_token_type_is_access_not_refresh() {
        when(jwtVerifier.verify(REFRESH_JWT)).thenReturn(refreshClaims(JwtTokenType.ACCESS));

        assertThatThrownBy(() -> service.refresh(new AuthRefreshCommand(REFRESH_JWT)))
                .isInstanceOf(RefreshTokenInvalidException.class);

        verify(refreshTokenStore, never()).find(any(), any());
        verify(refreshTokenStore, never()).findAllSessionIds(any());
        verify(sessionBlacklist, never()).blacklist(any(), any());
    }

    @Test
    void refresh_triggers_reuse_compensation_when_record_not_found() {
        when(jwtVerifier.verify(REFRESH_JWT)).thenReturn(refreshClaims(JwtTokenType.REFRESH));
        when(refreshTokenHasher.hash(REFRESH_JWT)).thenReturn("h");
        when(refreshTokenStore.find(USER_ID, OLD_SID)).thenReturn(Optional.empty());
        when(refreshTokenStore.findAllSessionIds(USER_ID))
                .thenReturn(new LinkedHashSet<>(List.of("sid-a", "sid-b", "sid-c")));

        assertThatThrownBy(() -> service.refresh(new AuthRefreshCommand(REFRESH_JWT)))
                .isInstanceOf(RefreshTokenReuseDetectedException.class);

        verify(sessionBlacklist).blacklist(eq("sid-a"), eq(EXPECTED_BLACKLIST_TTL));
        verify(sessionBlacklist).blacklist(eq("sid-b"), eq(EXPECTED_BLACKLIST_TTL));
        verify(sessionBlacklist).blacklist(eq("sid-c"), eq(EXPECTED_BLACKLIST_TTL));
        verify(refreshTokenStore).delete(USER_ID, "sid-a");
        verify(refreshTokenStore).delete(USER_ID, "sid-b");
        verify(refreshTokenStore).delete(USER_ID, "sid-c");
        verify(jwtIssuer, never()).issue(any());
    }

    @Test
    void refresh_triggers_reuse_compensation_when_hash_mismatches() {
        when(jwtVerifier.verify(REFRESH_JWT)).thenReturn(refreshClaims(JwtTokenType.REFRESH));
        when(refreshTokenHasher.hash(REFRESH_JWT)).thenReturn("submitted-hash");
        when(refreshTokenStore.find(USER_ID, OLD_SID))
                .thenReturn(Optional.of(storedRecord("DIFFERENT-stored-hash")));
        when(refreshTokenStore.findAllSessionIds(USER_ID))
                .thenReturn(new LinkedHashSet<>(List.of(OLD_SID)));

        assertThatThrownBy(() -> service.refresh(new AuthRefreshCommand(REFRESH_JWT)))
                .isInstanceOf(RefreshTokenReuseDetectedException.class);

        verify(sessionBlacklist).blacklist(eq(OLD_SID), eq(EXPECTED_BLACKLIST_TTL));
        verify(refreshTokenStore).delete(USER_ID, OLD_SID);
        verify(jwtIssuer, never()).issue(any());
    }

    @Test
    void refresh_compensation_attempts_all_sids_even_when_some_blacklist_calls_fail() {
        // 결정적 시나리오: LinkedHashSet 으로 sid-a, sid-b, sid-c 순회 보장.
        // sid-a, sid-c 는 blacklist 실패 — 그래도 sid-b 는 정상 처리되고 모든 sid 시도됨.
        when(jwtVerifier.verify(REFRESH_JWT)).thenReturn(refreshClaims(JwtTokenType.REFRESH));
        when(refreshTokenHasher.hash(REFRESH_JWT)).thenReturn("h");
        when(refreshTokenStore.find(USER_ID, OLD_SID)).thenReturn(Optional.empty());
        when(refreshTokenStore.findAllSessionIds(USER_ID))
                .thenReturn(new LinkedHashSet<>(List.of("sid-a", "sid-b", "sid-c")));
        doThrow(new RuntimeException("redis hiccup"))
                .when(sessionBlacklist).blacklist(eq("sid-a"), any());
        doThrow(new RuntimeException("redis hiccup"))
                .when(sessionBlacklist).blacklist(eq("sid-c"), any());

        assertThatThrownBy(() -> service.refresh(new AuthRefreshCommand(REFRESH_JWT)))
                .isInstanceOf(RefreshTokenReuseDetectedException.class);

        // 모든 sid 에 blacklist 시도
        verify(sessionBlacklist).blacklist(eq("sid-a"), eq(EXPECTED_BLACKLIST_TTL));
        verify(sessionBlacklist).blacklist(eq("sid-b"), eq(EXPECTED_BLACKLIST_TTL));
        verify(sessionBlacklist).blacklist(eq("sid-c"), eq(EXPECTED_BLACKLIST_TTL));
        // 성공한 sid-b 만 delete (sid-a/c 는 blacklist 실패로 catch → delete 미진행)
        verify(refreshTokenStore).delete(USER_ID, "sid-b");
        verify(refreshTokenStore, never()).delete(USER_ID, "sid-a");
        verify(refreshTokenStore, never()).delete(USER_ID, "sid-c");
    }

    @Test
    void refresh_throws_illegal_state_when_compensation_fails_for_all_sids() {
        when(jwtVerifier.verify(REFRESH_JWT)).thenReturn(refreshClaims(JwtTokenType.REFRESH));
        when(refreshTokenHasher.hash(REFRESH_JWT)).thenReturn("h");
        when(refreshTokenStore.find(USER_ID, OLD_SID)).thenReturn(Optional.empty());
        when(refreshTokenStore.findAllSessionIds(USER_ID))
                .thenReturn(new LinkedHashSet<>(List.of("sid-a", "sid-b")));
        doThrow(new RuntimeException("redis down")).when(sessionBlacklist).blacklist(any(), any());

        // 보상이 모든 sid 에서 실패하면 reuse 차단이 사실상 무력화 — 즉시 실패로 가시화.
        assertThatThrownBy(() -> service.refresh(new AuthRefreshCommand(REFRESH_JWT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reuse compensation completely failed");
    }
}
