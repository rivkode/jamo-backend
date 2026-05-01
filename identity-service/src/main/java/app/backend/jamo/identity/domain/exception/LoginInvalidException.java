package app.backend.jamo.identity.domain.exception;

/**
 * LOCAL email/password 로그인 실패.
 *
 * <p>계정 없음, OAuth-only 계정, 비밀번호 불일치를 모두 같은 예외로 통합해 계정 존재 여부를
 * 클라이언트에 노출하지 않는다.
 */
public class LoginInvalidException extends RuntimeException {

    public LoginInvalidException(String message) {
        super(message);
    }
}
