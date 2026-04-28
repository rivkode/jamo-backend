package app.backend.jamo.identity.presentation.controller;

import app.backend.jamo.identity.application.dto.MyProfileResult;
import app.backend.jamo.identity.application.dto.PublicProfileResult;
import app.backend.jamo.identity.application.dto.RetrieveMyProfileQuery;
import app.backend.jamo.identity.application.dto.RetrieveProfileQuery;
import app.backend.jamo.identity.application.dto.UpdateMyProfileCommand;
import app.backend.jamo.identity.application.service.RetrieveMyProfileService;
import app.backend.jamo.identity.application.service.RetrieveProfileService;
import app.backend.jamo.identity.application.service.UpdateMyProfileService;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.presentation.dto.MyProfileResponse;
import app.backend.jamo.identity.presentation.dto.PublicProfileResponse;
import app.backend.jamo.identity.presentation.dto.UpdateMyProfileRequest;
import app.backend.jamo.identity.presentation.web.AuthenticatedUser;
import app.backend.jamo.identity.presentation.web.LoginUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Profile 도메인 HTTP API (PRD profile/*).
 *
 * <p>3 endpoint:
 * <ul>
 *   <li>GET /api/v1/profiles/me — 본인 프로필 (private 8 필드)</li>
 *   <li>GET /api/v1/profiles/{userId} — 타 사용자 프로필 (public-safe 4 필드)</li>
 *   <li>PATCH /api/v1/profiles/me — 본인 프로필 부분 수정 (화이트리스트 4 필드)</li>
 * </ul>
 *
 * <p>모두 인증 필수 ({@code @LoginUser}). path userId 는 UUID 문자열.
 */
@RestController
@RequestMapping("/api/v1/profiles")
@SecurityRequirement(name = "BearerJwt")
public class ProfileController {

    private final RetrieveMyProfileService retrieveMyProfileService;
    private final RetrieveProfileService retrieveProfileService;
    private final UpdateMyProfileService updateMyProfileService;

    public ProfileController(RetrieveMyProfileService retrieveMyProfileService,
                             RetrieveProfileService retrieveProfileService,
                             UpdateMyProfileService updateMyProfileService) {
        this.retrieveMyProfileService = retrieveMyProfileService;
        this.retrieveProfileService = retrieveProfileService;
        this.updateMyProfileService = updateMyProfileService;
    }

    @GetMapping("/me")
    public ResponseEntity<MyProfileResponse> getMyProfile(@LoginUser AuthenticatedUser auth) {
        MyProfileResult result = retrieveMyProfileService.retrieve(
                new RetrieveMyProfileQuery(auth.userId()));
        return ResponseEntity.ok(MyProfileResponse.from(result));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<PublicProfileResponse> getProfile(@LoginUser AuthenticatedUser auth,
                                                            @PathVariable("userId") String userId) {
        // UUID parsing 실패 시 IAE — ProfileExceptionHandler.handleIllegalArgument 가 400 매핑 (code review H1)
        UserId targetId = new UserId(UUID.fromString(userId));
        PublicProfileResult result = retrieveProfileService.retrieve(
                new RetrieveProfileQuery(targetId));
        return ResponseEntity.ok(PublicProfileResponse.from(result));
    }

    @PatchMapping("/me")
    public ResponseEntity<MyProfileResponse> updateMyProfile(@LoginUser AuthenticatedUser auth,
                                                             @Valid @RequestBody UpdateMyProfileRequest request) {
        MyProfileResult result = updateMyProfileService.update(new UpdateMyProfileCommand(
                auth.userId(),
                request.displayName(),
                request.bio(),
                request.avatarUrl(),
                request.locale()));
        return ResponseEntity.ok(MyProfileResponse.from(result));
    }
}
