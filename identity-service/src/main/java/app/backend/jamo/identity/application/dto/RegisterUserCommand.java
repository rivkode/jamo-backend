package app.backend.jamo.identity.application.dto;

import java.util.Objects;

/**
 * LOCAL 회원가입 요청 입력 (PRD user/createUser.md).
 *
 * <p>{@code rawPassword} 는 평문 — Application Service 가 {@code PasswordEncoder.encode}
 * 직후 즉시 폐기하며, 본 객체는 service 진입 직후 단일 호출에서만 사용.
 * 직렬화 / 로깅 회피를 위해 {@code toString} 을 마스킹 처리.
 */
public record RegisterUserCommand(String email, String rawPassword, String displayName) {

    public RegisterUserCommand {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(rawPassword, "rawPassword");
        Objects.requireNonNull(displayName, "displayName");
    }

    @Override
    public String toString() {
        return "RegisterUserCommand[email=" + email + ", rawPassword=***, displayName=" + displayName + "]";
    }
}
