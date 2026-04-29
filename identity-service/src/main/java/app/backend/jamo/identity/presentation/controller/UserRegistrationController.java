package app.backend.jamo.identity.presentation.controller;

import app.backend.jamo.identity.application.dto.RegisterUserCommand;
import app.backend.jamo.identity.application.dto.RegisterUserResult;
import app.backend.jamo.identity.application.service.RegisterUserService;
import app.backend.jamo.identity.presentation.dto.RegisterUserRequest;
import app.backend.jamo.identity.presentation.dto.RegisterUserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User 도메인 LOCAL 회원가입 API (PRD user/createUser.md).
 *
 * <p>인증 미요구. 응답 201 Created + {@link RegisterUserResponse} (토큰 미발급).
 *
 * <p>예외 매핑은 {@code UserExceptionHandler}:
 * <ul>
 *   <li>{@code EmailNotValidatedException} → 400 {@code EMAIL_NOT_VALIDATED}</li>
 *   <li>{@code EmailAlreadyRegisteredException} → 409 {@code EMAIL_ALREADY_REGISTERED}</li>
 *   <li>Bean Validation / Domain VO IAE → 400 {@code VALIDATION_FAILED}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserRegistrationController {

    private final RegisterUserService registerUserService;

    @PostMapping
    public ResponseEntity<RegisterUserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        RegisterUserResult result = registerUserService.register(
                new RegisterUserCommand(request.email(), request.password(), request.displayName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterUserResponse.from(result));
    }
}
