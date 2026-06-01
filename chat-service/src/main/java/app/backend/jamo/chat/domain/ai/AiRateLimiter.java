package app.backend.jamo.chat.domain.ai;

/**
 * AI 생성 호출 rate limit port — 사용자 단위 AI 비용/남용 가드 (diarychat AI 자동응답, S4).
 *
 * <p>chat-service 가 AI 게이트웨이로서 사용량 정책을 소유 (module-boundary §AI). 최소 가드 — 정교한
 * quota/대시보드는 후속. 구현은 infrastructure (in-memory 고정 윈도우 카운터, 단일 인스턴스 한정).
 */
public interface AiRateLimiter {

    /**
     * 사용자에게 1회 호출을 허용하면 true 를 반환하며 사용량을 1 증가. 한도 초과 시 false (증가 없음).
     */
    boolean tryAcquire(String userId);
}
