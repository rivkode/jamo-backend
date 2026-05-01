package app.backend.jamo.identity.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/auth/login 의 Request body.
 */
public record AuthLoginRequest(
        @Email(message = "email must be valid")
        @NotBlank(message = "email must not be blank")
        @Size(max = 254, message = "email must be at most 254 characters")
        String email,

        @NotBlank(message = "password must not be blank")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String password
) {

    @Override
    public String toString() {
        return "AuthLoginRequest[email=" + email + ", password=***]";
    }
}

