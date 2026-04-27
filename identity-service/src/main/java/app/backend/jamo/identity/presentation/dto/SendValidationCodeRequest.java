package app.backend.jamo.identity.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/users/validation-number 의 Request body.
 *
 * <p>Bean Validation 1차 방어 (PRD user/sendValidationNumber.md §9):
 * <ul>
 *   <li>{@code @NotBlank} — 빈 값 거부</li>
 *   <li>{@code @Email} — RFC 형식 거부 (Domain Email VO 의 정규식보다 느슨하지만 1차 차단)</li>
 *   <li>{@code @Size(max=254)} — RFC 5321 SMTP envelope 한도</li>
 * </ul>
 */
public record SendValidationCodeRequest(
        @NotBlank(message = "email must not be blank")
        @Email(message = "email format is invalid")
        @Size(max = 254, message = "email must be at most 254 characters")
        String email
) {
}
