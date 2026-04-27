package app.backend.jamo.identity.presentation.controller;

import app.backend.jamo.identity.application.dto.AuthExchangeResult;
import app.backend.jamo.identity.application.dto.AuthRefreshCommand;
import app.backend.jamo.identity.application.service.AuthRefreshService;
import app.backend.jamo.identity.presentation.dto.AuthExchangeResponse;
import app.backend.jamo.identity.presentation.dto.AuthRefreshRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/auth/refresh — refresh JWT 회전 (PRD auth/refresh.md §9).
 *
 * <p>응답 DTO 는 exchange 와 동일 ({@link AuthExchangeResponse}) — refresh 는 토큰 페어
 * 재발급이라 의미적으로 동일.
 *
 * <p>예외 매핑은 {@code AuthExceptionHandler} — RefreshTokenExpired→REFRESH_EXPIRED,
 * RefreshTokenInvalid + ReuseDetected→REFRESH_INVALID 통합 (decisions Q2).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthRefreshController {

    private final AuthRefreshService authRefreshService;

    public AuthRefreshController(AuthRefreshService authRefreshService) {
        this.authRefreshService = authRefreshService;
    }

    @PostMapping("/refresh")
    public AuthExchangeResponse refresh(@Valid @RequestBody AuthRefreshRequest request) {
        AuthExchangeResult result = authRefreshService.refresh(
                new AuthRefreshCommand(request.refreshToken()));
        return new AuthExchangeResponse(
                result.userId().asString(),
                result.accessToken(),
                result.refreshToken(),
                result.expiresInSeconds()
        );
    }
}
