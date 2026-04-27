package app.backend.jamo.identity.application.dto;

/**
 * 이메일 검증코드 발송 요청 Command.
 *
 * <p>controller 가 raw email string 을 받아 그대로 담는다 — Email VO 변환은 application
 * service 가 수행 (AuthRefreshCommand 의 raw JWT 를 service 가 verify 하는 패턴과 동일).
 */
public record SendValidationCodeCommand(String email) {

    public SendValidationCodeCommand {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
    }
}
