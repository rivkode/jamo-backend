package app.backend.jamo.identity.presentation.controller;

import app.backend.jamo.identity.application.dto.SendValidationCodeCommand;
import app.backend.jamo.identity.application.dto.VerifyValidationCodeCommand;
import app.backend.jamo.identity.application.service.SendValidationCodeService;
import app.backend.jamo.identity.application.service.VerifyValidationCodeService;
import app.backend.jamo.identity.presentation.dto.SendValidationCodeRequest;
import app.backend.jamo.identity.presentation.dto.VerifyValidationCodeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User 도메인 이메일 검증 API (PRD user/sendValidationNumber.md, user/validateEmail.md).
 *
 * <p>두 endpoint 모두 인증 미요구 — 회원가입 전 흐름. 응답은 204 No Content (성공 시 데이터 없음).
 *
 * <p>예외 매핑은 {@code UserExceptionHandler}:
 * <ul>
 *   <li>ValidationCodeMismatch → 400 VALIDATION_CODE_MISMATCH</li>
 *   <li>ValidationCodeExpired  → 400 VALIDATION_CODE_EXPIRED</li>
 *   <li>ValidationCodeLocked   → 400 VALIDATION_CODE_LOCKED</li>
 *   <li>ValidationRateLimited  → 429 VALIDATION_RATE_LIMITED</li>
 *   <li>Bean Validation 실패   → 400 VALIDATION_FAILED ({@code AuthExceptionHandler} 의 매핑 재사용은
 *       동일 도메인 미적용 — 본 슬라이스에서는 AuthExceptionHandler 의 VALIDATION_FAILED 가 잡지만
 *       응답 enum 이 다름. 통합 그룹화는 PR4-c deferral M1 의 후속 PR 에서 처리)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserValidationController {

    private final SendValidationCodeService sendValidationCodeService;
    private final VerifyValidationCodeService verifyValidationCodeService;

    @PostMapping("/validation-number")
    public ResponseEntity<Void> sendValidationNumber(@Valid @RequestBody SendValidationCodeRequest request) {
        sendValidationCodeService.send(new SendValidationCodeCommand(request.email()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/validation-email")
    public ResponseEntity<Void> validateEmail(@Valid @RequestBody VerifyValidationCodeRequest request) {
        verifyValidationCodeService.verify(new VerifyValidationCodeCommand(request.email(), request.code()));
        return ResponseEntity.noContent().build();
    }
}
