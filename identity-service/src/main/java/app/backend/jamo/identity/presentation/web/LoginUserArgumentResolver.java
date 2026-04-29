package app.backend.jamo.identity.presentation.web;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.common.auth.JwtVerificationException;
import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.identity.domain.model.user.UserId;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link LoginUser} 파라미터 해석기 — Authorization 헤더의 Bearer token 을 access JWT 로
 * 검증해 {@link AuthenticatedUser} 를 주입한다.
 *
 * <p>검증 단계:
 * <ol>
 *   <li>{@code Authorization: Bearer <token>} 헤더 존재 + Bearer prefix</li>
 *   <li>{@link JwtVerifier#verify} — sig + exp + issuer + audience + sid blacklist</li>
 *   <li>tokenType=ACCESS 강제 (refresh JWT 로 보호 endpoint 호출 차단)</li>
 * </ol>
 *
 * <p>모든 실패는 {@link UnauthorizedException} 으로 통일 — 만료/위조/blacklist 분기 신호를
 * 클라이언트에 노출하지 않는다 (보안 표준, decisions Q3).
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
                    "access token verification failed: " + e.getClass().getSimpleName(), e);
        }
        if (claims.tokenType() != JwtTokenType.ACCESS) {
            throw new UnauthorizedException("token is not an access token");
        }
        UserId userId;
        try {
            userId = UserId.fromString(claims.subject());
        } catch (IllegalArgumentException e) {
            // 정상 RsaJwtIssuer 는 항상 UUID subject — 비표준 subject 는 토큰 위조/오발급 의심.
            // 500 (handleGeneric) 으로 빠지지 않도록 401 로 매핑 (security L1).
            throw new UnauthorizedException("subject claim is not a valid user id", e);
        }
        try {
            return new AuthenticatedUser(userId, claims.sessionId(), claims.deviceId());
        } catch (IllegalArgumentException | NullPointerException e) {
            // sessionId / deviceId 부재한 토큰은 토큰 발급 측 결함 — 401 일관 매핑 (security review M3).
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
