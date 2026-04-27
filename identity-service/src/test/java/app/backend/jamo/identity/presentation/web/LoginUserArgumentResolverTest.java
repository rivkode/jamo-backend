package app.backend.jamo.identity.presentation.web;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtExpiredException;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.common.auth.JwtVerificationException;
import app.backend.jamo.common.auth.JwtVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.NativeWebRequest;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginUserArgumentResolverTest {

    private JwtVerifier jwtVerifier;
    private LoginUserArgumentResolver resolver;
    private MethodParameter loginUserParam;
    private MethodParameter unannotatedParam;
    private MethodParameter wrongTypeParam;

    static class TargetMethods {
        public void annotated(@LoginUser AuthenticatedUser user) {}
        public void unannotated(AuthenticatedUser user) {}
        public void wrongType(@LoginUser String user) {}
    }

    @BeforeEach
    void setUp() throws Exception {
        jwtVerifier = mock(JwtVerifier.class);
        resolver = new LoginUserArgumentResolver(jwtVerifier);
        Method annotated = TargetMethods.class.getMethod("annotated", AuthenticatedUser.class);
        Method unannotated = TargetMethods.class.getMethod("unannotated", AuthenticatedUser.class);
        Method wrongType = TargetMethods.class.getMethod("wrongType", String.class);
        loginUserParam = new MethodParameter(annotated, 0);
        unannotatedParam = new MethodParameter(unannotated, 0);
        wrongTypeParam = new MethodParameter(wrongType, 0);
    }

    @Test
    void supportsParameter_only_for_loginUser_annotated_AuthenticatedUser() {
        assertThat(resolver.supportsParameter(loginUserParam)).isTrue();
        assertThat(resolver.supportsParameter(unannotatedParam)).isFalse();
        assertThat(resolver.supportsParameter(wrongTypeParam)).isFalse();
    }

    private NativeWebRequest mockRequest(String authorizationHeader) {
        HttpServletRequest http = mock(HttpServletRequest.class);
        when(http.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authorizationHeader);
        NativeWebRequest req = mock(NativeWebRequest.class);
        when(req.getNativeRequest(HttpServletRequest.class)).thenReturn(http);
        return req;
    }

    private JwtClaims accessClaims() {
        Instant now = Instant.parse("2026-04-27T10:00:00Z");
        return new JwtClaims("01234567-89ab-cdef-0123-456789abcdef",
                "sid-1", "device-1",
                JwtTokenType.ACCESS, now, now.plusSeconds(900));
    }

    @Test
    void resolveArgument_returns_authenticated_user_for_valid_access_jwt() throws Exception {
        when(jwtVerifier.verify("valid-token")).thenReturn(accessClaims());

        Object result = resolver.resolveArgument(loginUserParam, null,
                mockRequest("Bearer valid-token"), null);

        assertThat(result).isInstanceOf(AuthenticatedUser.class);
        AuthenticatedUser user = (AuthenticatedUser) result;
        assertThat(user.userId().asString()).isEqualTo("01234567-89ab-cdef-0123-456789abcdef");
        assertThat(user.sessionId()).isEqualTo("sid-1");
        assertThat(user.deviceId()).isEqualTo("device-1");
    }

    @Test
    void resolveArgument_throws_unauthorized_when_header_missing() {
        assertThatThrownBy(() -> resolver.resolveArgument(loginUserParam, null,
                mockRequest(null), null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void resolveArgument_throws_unauthorized_when_header_blank() {
        assertThatThrownBy(() -> resolver.resolveArgument(loginUserParam, null,
                mockRequest("   "), null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void resolveArgument_throws_unauthorized_when_bearer_prefix_missing() {
        assertThatThrownBy(() -> resolver.resolveArgument(loginUserParam, null,
                mockRequest("valid-token"), null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void resolveArgument_throws_unauthorized_when_bearer_token_blank() {
        assertThatThrownBy(() -> resolver.resolveArgument(loginUserParam, null,
                mockRequest("Bearer    "), null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void resolveArgument_throws_unauthorized_when_jwt_expired() {
        when(jwtVerifier.verify("expired-token"))
                .thenThrow(new JwtExpiredException("token expired"));

        assertThatThrownBy(() -> resolver.resolveArgument(loginUserParam, null,
                mockRequest("Bearer expired-token"), null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void resolveArgument_throws_unauthorized_when_jwt_signature_invalid() {
        when(jwtVerifier.verify("forged-token"))
                .thenThrow(new JwtVerificationException("invalid signature"));

        assertThatThrownBy(() -> resolver.resolveArgument(loginUserParam, null,
                mockRequest("Bearer forged-token"), null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void resolveArgument_throws_unauthorized_when_sid_is_blacklisted() {
        // RsaJwtVerifier 가 blacklist sid 시 던지는 사양 (BlacklistChecker 호출 결과) 도
        // 일반 JwtVerificationException 분기와 동일하게 UnauthorizedException 으로 매핑되어야 한다.
        when(jwtVerifier.verify("blacklisted-token"))
                .thenThrow(new JwtVerificationException("session blacklisted"));

        assertThatThrownBy(() -> resolver.resolveArgument(loginUserParam, null,
                mockRequest("Bearer blacklisted-token"), null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void resolveArgument_throws_unauthorized_when_token_type_is_refresh() {
        Instant now = Instant.parse("2026-04-27T10:00:00Z");
        JwtClaims refreshClaims = new JwtClaims("01234567-89ab-cdef-0123-456789abcdef",
                "sid-1", "device-1",
                JwtTokenType.REFRESH, now, now.plusSeconds(900));
        when(jwtVerifier.verify("refresh-token")).thenReturn(refreshClaims);

        assertThatThrownBy(() -> resolver.resolveArgument(loginUserParam, null,
                mockRequest("Bearer refresh-token"), null))
                .isInstanceOf(UnauthorizedException.class);
    }
}
