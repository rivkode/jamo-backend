package app.backend.jamo.identity.presentation.controller;

import app.backend.jamo.identity.application.dto.OAuthCallbackCommand;
import app.backend.jamo.identity.application.dto.OAuthCallbackResult;
import app.backend.jamo.identity.application.dto.OAuthStartCommand;
import app.backend.jamo.identity.application.dto.OAuthStartResult;
import app.backend.jamo.identity.application.service.OAuthCallbackService;
import app.backend.jamo.identity.application.service.OAuthStartService;
import app.backend.jamo.identity.domain.exception.OAuthAuthenticationException;
import app.backend.jamo.identity.infrastructure.config.FrontendProperties;
import app.backend.jamo.identity.domain.exception.OAuthFlowExpiredException;
import app.backend.jamo.identity.domain.exception.OAuthProviderCallFailedException;
import app.backend.jamo.identity.domain.exception.OAuthStateInvalidException;
import app.backend.jamo.identity.domain.model.auth.AuthState;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.presentation.dto.AuthErrorCode;
import app.backend.jamo.identity.presentation.web.DeviceIdResolver;
import app.backend.jamo.identity.presentation.web.DeviceIdResult;
import app.backend.jamo.identity.presentation.web.StateCookieManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * OAuth start / callback browser endpoints (PRD auth/start.md, auth/callback.md).
 *
 * <p>Start: 302 + Set-Cookie(oauth_state_<provider>) → provider authorize URL.
 * Callback: 302 → frontend (성공: /auth/callback?code, 실패: /auth/error?code).
 *
 * <p>Callback 의 모든 OAuth 예외는 본 controller 가 try-catch 후 frontend redirect 로
 * 매핑 — ExceptionHandler 미경유 (PRD 명시). state cookie 는 finally 에서 clear.
 */
@RestController
@RequestMapping("/api/v1/auth/oauth")
public class OAuthBrowserController {

    private static final Logger log = LoggerFactory.getLogger(OAuthBrowserController.class);

    private static final String FRONTEND_CALLBACK_PATH = "/auth/callback";
    private static final String FRONTEND_ERROR_PATH = "/auth/error";

    /** OAuth 2.0 RFC 6749 §4.1.2.1 의 표준 error code 화이트리스트 (log injection 차단, security review M1). */
    private static final Pattern OAUTH_ERROR_CODE_PATTERN = Pattern.compile("^[a-z_]{1,40}$");

    private final OAuthStartService oAuthStartService;
    private final OAuthCallbackService oAuthCallbackService;
    private final StateCookieManager stateCookieManager;
    private final DeviceIdResolver deviceIdResolver;
    private final String frontendBaseUrl;

    public OAuthBrowserController(OAuthStartService oAuthStartService,
                                  OAuthCallbackService oAuthCallbackService,
                                  StateCookieManager stateCookieManager,
                                  DeviceIdResolver deviceIdResolver,
                                  FrontendProperties frontendProperties) {
        this.oAuthStartService = oAuthStartService;
        this.oAuthCallbackService = oAuthCallbackService;
        this.stateCookieManager = stateCookieManager;
        this.deviceIdResolver = deviceIdResolver;
        this.frontendBaseUrl = frontendProperties.baseUrl();
    }

    @GetMapping("/{provider}/start")
    public ResponseEntity<Void> start(@PathVariable("provider") String providerName,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        OAuthProvider provider = OAuthProvider.fromExternal(providerName);

        DeviceIdResult deviceResult = deviceIdResolver.resolve(request);
        OAuthStartResult result = oAuthStartService.start(
                new OAuthStartCommand(provider, deviceResult.deviceId()));

        stateCookieManager.set(response, provider, result.state());
        if (deviceResult.isNewlyGenerated()) {
            deviceIdResolver.setDeviceCookie(response, deviceResult.deviceId());
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(result.authorizeUrl()))
                .build();
    }

    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable("provider") String providerName,
                                         @RequestParam(value = "code", required = false) String code,
                                         @RequestParam(value = "state", required = false) String state,
                                         @RequestParam(value = "error", required = false) String error,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        OAuthProvider provider;
        try {
            provider = OAuthProvider.fromExternal(providerName);
        } catch (OAuthAuthenticationException ex) {
            // provider 자체를 모르므로 state cookie clear 시도 안 함
            return errorRedirect(AuthErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
        return handleCallback(provider, code, state, error, request, response);
    }

    private ResponseEntity<Void> handleCallback(OAuthProvider provider,
                                                String code,
                                                String state,
                                                String error,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {
        try {
            if (error != null && !error.isBlank()) {
                String safeError = OAUTH_ERROR_CODE_PATTERN.matcher(error).matches()
                        ? error
                        : "<invalid>";
                log.info("oauth callback provider={} error={}", provider, safeError);
                return errorRedirect(AuthErrorCode.OAUTH_AUTHORIZATION_FAILED);
            }
            if (code == null || code.isBlank() || state == null || state.isBlank()) {
                return errorRedirect(AuthErrorCode.OAUTH_AUTHORIZATION_FAILED);
            }

            AuthState receivedState;
            try {
                receivedState = new AuthState(state);
            } catch (IllegalArgumentException invalid) {
                return errorRedirect(AuthErrorCode.OAUTH_STATE_INVALID);
            }

            AuthState cookieState = stateCookieManager.read(request, provider).orElse(null);

            OAuthCallbackResult result = oAuthCallbackService.handle(new OAuthCallbackCommand(
                    provider, code, receivedState, cookieState));
            return successRedirect(result);

        } catch (OAuthStateInvalidException ex) {
            return errorRedirect(AuthErrorCode.OAUTH_STATE_INVALID);
        } catch (OAuthFlowExpiredException ex) {
            return errorRedirect(AuthErrorCode.OAUTH_FLOW_EXPIRED);
        } catch (OAuthProviderCallFailedException ex) {
            return errorRedirect(AuthErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
        } catch (OAuthAuthenticationException ex) {
            return errorRedirect(AuthErrorCode.OAUTH_AUTHORIZATION_FAILED);
        } finally {
            stateCookieManager.clear(response, provider);
        }
    }

    private ResponseEntity<Void> successRedirect(OAuthCallbackResult result) {
        String url = UriComponentsBuilder.fromUriString(frontendBaseUrl + FRONTEND_CALLBACK_PATH)
                .queryParam("code", result.authCode())
                .queryParam("isNew", result.isNewUser())
                .queryParam("truncated", result.displayNameTruncated())
                .build().encode().toUriString();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }

    private ResponseEntity<Void> errorRedirect(AuthErrorCode errorCode) {
        String url = UriComponentsBuilder.fromUriString(frontendBaseUrl + FRONTEND_ERROR_PATH)
                .queryParam("code", errorCode.name())
                .build().encode().toUriString();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }
}
