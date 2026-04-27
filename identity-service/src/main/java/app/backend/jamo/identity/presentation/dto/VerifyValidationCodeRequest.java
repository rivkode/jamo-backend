package app.backend.jamo.identity.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/users/validation-email 의 Request body.
 *
 * <p>Bean Validation 1차 방어 (PRD user/validateEmail.md §9):
 * <ul>
 *   <li>{@code email} — {@code @NotBlank} + {@code @Email} + {@code @Size(max=254)}</li>
 *   <li>{@code code} — {@code @NotBlank} + {@code @Pattern("\\d{6}")} 정확히 6자리 숫자</li>
 * </ul>
 *
 * <p>application service 의 {@code ValidationCode.of()} 가 동일 정규식으로 2차 방어.
 */
public record VerifyValidationCodeRequest(
        @NotBlank(message = "email must not be blank")
        @Email(message = "email format is invalid")
        @Size(max = 254, message = "email must be at most 254 characters")
        String email,

        @NotBlank(message = "code must not be blank")
        @Pattern(regexp = "\\d{6}", message = "code must be exactly 6 digits")
        String code
) {
}
