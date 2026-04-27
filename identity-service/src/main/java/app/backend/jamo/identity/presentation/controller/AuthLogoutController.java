package app.backend.jamo.identity.presentation.controller;

import app.backend.jamo.identity.application.dto.AuthLogoutCommand;
import app.backend.jamo.identity.application.service.AuthLogoutService;
import app.backend.jamo.identity.presentation.web.AuthenticatedUser;
import app.backend.jamo.identity.presentation.web.LoginUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/auth/logout — 단일 디바이스 로그아웃 (PRD auth/logout.md §9).
 *
 * <p>인증 필수 — {@link LoginUser} 가 access JWT 를 검증해 sid/userId 추출. 인증 실패는
 * {@code AuthExceptionHandler} 가 401 + {@code UNAUTHORIZED} 매핑.
 *
 * <p>전 디바이스 로그아웃 ({@code POST /auth/logout/all}) 은 별도 endpoint 로 분리 예정 (PR4+).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthLogoutController {

    private final AuthLogoutService authLogoutService;

    public AuthLogoutController(AuthLogoutService authLogoutService) {
        this.authLogoutService = authLogoutService;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@LoginUser AuthenticatedUser user) {
        authLogoutService.logout(new AuthLogoutCommand(user.userId(), user.sessionId()));
        return ResponseEntity.noContent().build();
    }
}
