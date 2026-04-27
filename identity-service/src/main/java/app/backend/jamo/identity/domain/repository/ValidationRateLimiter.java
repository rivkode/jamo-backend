package app.backend.jamo.identity.domain.repository;

import app.backend.jamo.identity.domain.model.user.Email;

/**
 * 이메일 검증코드 발송 rate limiter port.
 *
 * <p>per-email 정책 (PRD user/sendValidationNumber.md §9):
 * <ul>
 *   <li>30초 쿨다운 — 직전 발송 후 30초 안에 재요청 거부</li>
 *   <li>1일 10회 한도 — 동일 email 에 대해 24h 내 11번째 발송부터 거부</li>
 * </ul>
 *
 * <p>{@link #canSend} 는 read-only 판정. 발송이 실제 성공한 시점에 호출자가 {@link #recordSend}
 * 로 카운터를 증가시키는 책임 분리 (CAS 가 아니므로 application service 에서 try-then-record 패턴).
 */
public interface ValidationRateLimiter {

    boolean canSend(Email email);

    void recordSend(Email email);
}
