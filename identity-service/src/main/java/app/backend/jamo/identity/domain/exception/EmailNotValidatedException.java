package app.backend.jamo.identity.domain.exception;

/**
 * createUser 진입 시 이메일 검증 flag 가 없거나 만료된 경우 (PRD user/createUser.md §9 FIX 항목).
 *
 * <p>{@code EmailValidatedFlag.consume(email)} 이 false 를 반환하면 던진다. presentation
 * 매핑은 400 {@code EMAIL_NOT_VALIDATED} — 메시지에는 어떤 사유(미발급/만료) 인지 포함하지 않음
 * (enumeration 회피, PR5-b security review H2 와 동일 정책).
 */
public class EmailNotValidatedException extends RuntimeException {

    public EmailNotValidatedException(String message) {
        super(message);
    }
}
