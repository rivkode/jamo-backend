package app.backend.jamo.identity.presentation.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/users 의 Request body — LOCAL 회원가입.
 *
 * <p>Bean Validation 1차 방어 (PRD user/createUser.md §9):
 * <ul>
 *   <li>{@code email} — {@code @NotBlank @Email} + RFC 5321 길이 한도 254. Domain {@link
 *       app.backend.jamo.identity.domain.model.user.Email} VO 의 정규식이 더 엄격하므로
 *       application service 진입 시 한 번 더 검증.</li>
 *   <li>{@code password} — {@code @NotBlank @Size(8, 72)}. 상한 72 는 BCrypt byte 한도
 *       (Spring Security {@code BCryptPasswordEncoder} 기준). 하한 8 은 최소 권장치.</li>
 *   <li>{@code displayName} — {@code @NotBlank @Size(1, 32)}. Domain {@link
 *       app.backend.jamo.identity.domain.model.user.DisplayName} VO 와 동일 한도.</li>
 * </ul>
 *
 * <p>본 DTO 는 presentation 진입점 전용. {@link app.backend.jamo.identity.application.dto.RegisterUserCommand}
 * 로 변환된 후에는 직렬화 / 로깅 금지 (rawPassword 평문 보유 — security review L2).
 *
 * <p><b>Defense in depth</b> (PR6-c security review M1):
 * <ul>
 *   <li>{@code password} 필드에 {@code @JsonProperty(WRITE_ONLY)} — 외부 직렬화 시 누락.
 *       inbound 역직렬화는 정상.</li>
 *   <li>{@code toString} override — {@code log.info("body={}", request)} 같은 실수 회피.</li>
 * </ul>
 *
 * <p><b>PRD 0526_flutter.md §1.2 정합 (Slice 2)</b>: {@code displayName} 에 {@code @JsonAlias("username")}
 * 추가 — frontend 가 {@code "username"} 으로 보내도 동일 필드 매핑. 응답 측 {@code username} 노출은
 * {@link RegisterUserResponse} 참조.
 */
public record RegisterUserRequest(
        @NotBlank(message = "email must not be blank")
        @Email(message = "email format is invalid")
        @Size(max = 254, message = "email must be at most 254 characters")
        String email,

        @NotBlank(message = "password must not be blank")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String password,

        @NotBlank(message = "displayName must not be blank")
        @Size(min = 1, max = 32, message = "displayName must be between 1 and 32 characters")
        @JsonAlias("username")
        String displayName
) {

    @Override
    public String toString() {
        return "RegisterUserRequest[email=" + email + ", password=***, displayName=" + displayName + "]";
    }
}
