package app.backend.jamo.identity.presentation.controller;

import app.backend.jamo.identity.application.dto.AuthExchangeResult;
import app.backend.jamo.identity.application.dto.AuthLoginCommand;
import app.backend.jamo.identity.application.service.AuthLoginService;
import app.backend.jamo.identity.presentation.dto.AuthExchangeResponse;
import app.backend.jamo.identity.presentation.dto.AuthLoginRequest;
import app.backend.jamo.identity.presentation.web.DeviceIdResolver;
import app.backend.jamo.identity.presentation.web.DeviceIdResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthLoginController {

    private final AuthLoginService authLoginService;
    private final DeviceIdResolver deviceIdResolver;

    @PostMapping("/login")
    public AuthExchangeResponse login(@Valid @RequestBody AuthLoginRequest request,
                                      HttpServletRequest httpRequest,
                                      HttpServletResponse httpResponse) {
        DeviceIdResult deviceResult = deviceIdResolver.resolve(httpRequest);
        AuthExchangeResult result = authLoginService.login(
                new AuthLoginCommand(
                        request.email(),
                        request.password(),
                        deviceResult.deviceId(),
                        clientIp(httpRequest)));
        if (deviceResult.isNewlyGenerated()) {
            deviceIdResolver.setDeviceCookie(httpResponse, deviceResult.deviceId());
        }
        return new AuthExchangeResponse(
                result.userId().asString(),
                result.accessToken(),
                result.refreshToken(),
                result.expiresInSeconds()
        );
    }

    private static String clientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr;
    }
}
