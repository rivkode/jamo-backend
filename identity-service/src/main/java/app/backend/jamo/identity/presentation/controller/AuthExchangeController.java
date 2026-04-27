package app.backend.jamo.identity.presentation.controller;

import app.backend.jamo.identity.application.dto.AuthExchangeCommand;
import app.backend.jamo.identity.application.dto.AuthExchangeResult;
import app.backend.jamo.identity.application.service.AuthExchangeService;
import app.backend.jamo.identity.presentation.dto.AuthExchangeRequest;
import app.backend.jamo.identity.presentation.dto.AuthExchangeResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/auth/exchange — OAuth callback 의 일회성 authorization code 를 JWT 페어로 교환.
 *
 * <p>예외는 {@link app.backend.jamo.identity.presentation.exception.AuthExceptionHandler} 가
 * 표준 ErrorCode 로 매핑 — 도메인 예외 raw message 는 응답에 노출되지 않는다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthExchangeController {

    private final AuthExchangeService authExchangeService;

    public AuthExchangeController(AuthExchangeService authExchangeService) {
        this.authExchangeService = authExchangeService;
    }

    @PostMapping("/exchange")
    public AuthExchangeResponse exchange(@Valid @RequestBody AuthExchangeRequest request) {
        AuthExchangeResult result = authExchangeService.exchange(new AuthExchangeCommand(request.code()));
        return new AuthExchangeResponse(
                result.userId().asString(),
                result.accessToken(),
                result.refreshToken(),
                result.expiresInSeconds()
        );
    }
}
