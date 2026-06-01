package app.backend.jamo.chat.presentation.web;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.common.auth.JwtVerificationException;
import app.backend.jamo.common.auth.JwtVerifier;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * {@link LoginUser} 파라미터 해석기 — Authorization Bearer access JWT 검증 → {@link AuthenticatedUser} 주입.
 * identity/diary 정합: sig+exp+issuer+audience+sid blacklist, tokenType=ACCESS 강제, 모든 실패는
 * {@link UnauthorizedException} 통일 (분기 신호 비노출).
 */
@Component
@RequiredArgsConstructor
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtVerifier jwtVerifier;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
            && parameter.getParameterType().equals(AuthenticatedUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new UnauthorizedException("native servlet request unavailable");
        }
        String token = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));

        JwtClaims claims;
        try {
            claims = jwtVerifier.verify(token);
        } catch (JwtVerificationException e) {
            throw new UnauthorizedException(
                "access token verification failed: " + e.getClass().getSimpleName());
        }
        if (claims.tokenType() != JwtTokenType.ACCESS) {
            throw new UnauthorizedException("token is not an access token");
        }
        UUID userId;
        try {
            userId = UUID.fromString(claims.subject());
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("subject claim is not a valid user id", e);
        }
        try {
            return new AuthenticatedUser(userId, claims.sessionId(), claims.deviceId());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UnauthorizedException("authenticated user context invalid", e);
        }
    }

    private static String extractBearerToken(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            throw new UnauthorizedException("authorization header missing");
        }
        if (!headerValue.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("authorization header is not Bearer scheme");
        }
        String token = headerValue.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            throw new UnauthorizedException("authorization Bearer token is blank");
        }
        return token;
    }
}
