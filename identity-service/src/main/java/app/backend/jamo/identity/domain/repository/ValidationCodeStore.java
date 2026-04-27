package app.backend.jamo.identity.domain.repository;

import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.ValidationCode;

import java.time.Duration;
import java.util.Optional;

/**
 * 이메일 검증코드 + 시도 카운터 보관소 port.
 *
 * <p>코드와 attempts 카운터는 동일 라이프사이클(TTL 5분)이므로 한 port 로 묶는다
 * (결정 문서: {@code docs/decisions/identity/user-validation-port-split.md}).
 * rate limit 카운터({@link ValidationRateLimiter}) 와 email_validated flag({@link EmailValidatedFlag}) 는
 * 별개 라이프사이클이므로 별도 port.
 *
 * <ul>
 *   <li>{@link #issue} — TTL 은 호출자가 산정해 전달 (예: properties 의 {@code validation.code-ttl}). 동일 email 의
 *       기존 코드와 attempts 는 덮어쓴다(재발급).</li>
 *   <li>{@link #incrementAttempts} — 시도 카운터 1 증가 후 누적 시도 횟수 반환. 호출자가 한도(5)
 *       초과 판정 시 {@link #invalidate} 로 코드 무효화 + lock 예외 throw.</li>
 *   <li>{@link #invalidate} — 코드 + attempts 동시 삭제. 검증 성공 시 / 잠금 시 호출.</li>
 * </ul>
 */
public interface ValidationCodeStore {

    void issue(Email email, ValidationCode code, Duration ttl);

    Optional<ValidationCode> find(Email email);

    int incrementAttempts(Email email);

    void invalidate(Email email);
}
