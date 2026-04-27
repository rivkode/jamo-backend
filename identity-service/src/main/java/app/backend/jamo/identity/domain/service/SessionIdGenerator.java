package app.backend.jamo.identity.domain.service;

/**
 * sessionId(sid) 발급 port.
 *
 * <p>OAuth callback 시 신규 세션 발급 + refresh 회전 시 기존 sid 폐기 후 신규 발급에 공통 사용.
 * 구현체는 충돌 확률이 무시 가능한 무작위 식별자 (예: UUID v4) 를 반환해야 한다.
 */
public interface SessionIdGenerator {

    String newSessionId();
}
