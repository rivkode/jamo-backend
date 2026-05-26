package app.backend.jamo.identity.presentation.controller;

import app.backend.jamo.identity.presentation.dto.AuthExchangeResponse;
import app.backend.jamo.identity.presentation.dto.AuthLoginRequest;
import app.backend.jamo.identity.presentation.dto.MyProfileResponse;
import app.backend.jamo.identity.presentation.dto.PublicProfileResponse;
import app.backend.jamo.identity.presentation.web.AuthenticatedUser;
import app.backend.jamo.identity.presentation.web.LoginUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PRD 0526_flutter.md §1 경로 정합용 alias controller (Slice 2).
 *
 * <p>3 endpoint:
 * <ul>
 *   <li>POST /api/v1/users/login → {@link AuthLoginController#login} 위임 (PRD §1.1).</li>
 *   <li>GET /api/v1/users/me → {@link ProfileController#getMyProfile} 위임 (PRD §1.5).</li>
 *   <li>GET /api/v1/users/{userId} → {@link ProfileController#getProfile} 위임 (PRD §1.6).</li>
 * </ul>
 *
 * <p>기존 {@code /api/v1/auth/*} 및 {@code /api/v1/profiles/*} controller 는 변경 없이 유지 — 새 path 만
 * 추가 (legacy 호환). 비즈니스 로직 / 응답 형태는 위임 대상과 100% 동일. 디바이스 쿠키 / 인증 / Bean Validation
 * 모두 위임 controller 의 흐름을 그대로 재사용.
 *
 * <p><b>alias controller 분리 이유</b>: 기존 controller 의 {@code @RequestMapping} 을 다중 path 로 바꾸는 대신
 * 신규 클래스로 분리해 grep 친화성 + 책임 응집성 확보. Slice 1 CommentController 의 full path 의도 박제와 정합.
 *
 * <p><b>Controller→Controller 위임 패턴 채택 이유 (code-reviewer M2)</b>: Application Service 직접 호출 시
 * {@link AuthLoginController} 의 {@code DeviceIdResolver.resolve()} / {@code setDeviceCookie()} /
 * {@code clientIp(httpRequest)} 추출 로직을 본 controller 에 중복 작성해야 한다. 위임 시 이 로직이 단일 진실
 * 위치에 유지된다 — Spring DI 가 두 controller bean 을 모두 등록하므로 정상 동작. 의존 그래프가 wider 해지는
 * trade-off 는 수용.
 *
 * <p><b>{@code @SecurityRequirement} 위치 (code-reviewer L2)</b>: {@code /login} 은 인증 불필요라 클래스 레벨
 * 어노테이션 불가 — 메서드 레벨 명시. {@link ProfileController} 는 모든 메서드가 인증 필요라 클래스 레벨.
 *
 * <p><b>alias 제거 정책</b>: 박제 {@code docs/decisions/api/spec-alias-removal-plan.md} — frontend 가 정식
 * path ({@code /api/v1/auth/login}, {@code /api/v1/profiles/me}, {@code /api/v1/profiles/{id}}) 사용으로
 * 전환 후 deprecation 단계 → 제거.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserPathAliasController {

    private final AuthLoginController authLoginController;
    private final ProfileController profileController;

    @PostMapping("/login")
    public AuthExchangeResponse login(@Valid @RequestBody AuthLoginRequest request,
                                      HttpServletRequest httpRequest,
                                      HttpServletResponse httpResponse) {
        return authLoginController.login(request, httpRequest, httpResponse);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "BearerJwt")
    public ResponseEntity<MyProfileResponse> getMyProfile(@LoginUser AuthenticatedUser auth) {
        return profileController.getMyProfile(auth);
    }

    @GetMapping("/{userId}")
    @SecurityRequirement(name = "BearerJwt")
    public ResponseEntity<PublicProfileResponse> getProfile(@LoginUser AuthenticatedUser auth,
                                                            @PathVariable("userId") String userId) {
        return profileController.getProfile(auth, userId);
    }
}
