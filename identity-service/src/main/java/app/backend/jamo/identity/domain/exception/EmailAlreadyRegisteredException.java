package app.backend.jamo.identity.domain.exception;

/**
 * LOCAL 가입 시 동일 email 의 LOCAL 계정이 이미 존재 (PRD user/createUser.md §9 FIX 항목).
 *
 * <p>OAuth 가입자와의 email 충돌은 ADR-0006 결정 4 에 따라 거부하지 않는다 — 본 예외는
 * {@code account_type=LOCAL} 한정 중복에서만 발생. presentation 매핑은 409 {@code EMAIL_ALREADY_REGISTERED}.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String message) {
        super(message);
    }
}
