package app.backend.jamo.identity.application.dto;

/**
 * 이메일 검증코드 대조 요청 Command.
 *
 * <p>raw email + raw 6자리 코드를 그대로 담는다. Email / ValidationCode VO 변환은
 * application service 에서 수행 (변환 실패 시 IllegalArgumentException — controller 의
 * @Valid 가 1차 검증).
 */
public record VerifyValidationCodeCommand(String email, String code) {

    public VerifyValidationCodeCommand {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
    }
}
