package app.backend.jamo.identity.domain.repository;

import app.backend.jamo.identity.domain.model.user.Email;

/**
 * LOCAL email/password 로그인 실패 시도 제한 port.
 *
 * <p>구현체는 email, client IP, deviceId 조합을 기준으로 실패 횟수를 제한하되 원문 email 을
 * 외부 저장소 key 에 그대로 노출하지 않는 것이 좋다.
 */
public interface LoginRateLimiter {

    boolean isAllowed(Email email, String clientIp, String deviceId);

    void recordFailure(Email email, String clientIp, String deviceId);

    void reset(Email email, String clientIp, String deviceId);
}
