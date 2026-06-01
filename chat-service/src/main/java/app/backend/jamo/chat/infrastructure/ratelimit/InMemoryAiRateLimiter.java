package app.backend.jamo.chat.infrastructure.ratelimit;

import app.backend.jamo.chat.domain.ai.AiRateLimiter;
import app.backend.jamo.chat.infrastructure.config.AiRateLimitProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자 단위 고정 윈도우 in-memory rate limiter — diarychat AI 비용/남용 최소 가드 (S4).
 *
 * <p><b>한계</b>: 단일 인스턴스 in-memory. 다중 인스턴스에서는 인스턴스별 독립 카운팅 → 실효 한도가
 * (instances × maxPerWindow). 정확한 분산 한도 / 대시보드는 후속 PR (Redis INCR + TTL 등).
 * 메모리 누수 방지: 만료된 윈도우는 다음 접근 시 재설정(reset) — 비활성 사용자 엔트리는 잔존하나
 * userId 수 상한(가입자) 이내라 수용. 필요 시 후속에서 주기적 sweep.
 */
@Component
public class InMemoryAiRateLimiter implements AiRateLimiter {

    private final int maxPerWindow;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public InMemoryAiRateLimiter(AiRateLimitProperties props, Clock clock) {
        this.maxPerWindow = props.maxPerWindow();
        this.windowMillis = props.window().toMillis();
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String userId) {
        long now = clock.millis();
        Window window = windows.computeIfAbsent(userId, k -> new Window(now));
        return window.tryAcquire(now);
    }

    /** 사용자별 고정 윈도우 — windowStart 경과 시 카운트/시작점 재설정. 전 메서드 monitor 락 (code-reviewer M2). */
    private final class Window {
        private long windowStart;
        private int count;

        private Window(long now) {
            this.windowStart = now;
        }

        private synchronized boolean tryAcquire(long now) {
            if (now - windowStart >= windowMillis) {
                windowStart = now;
                count = 0;
            }
            if (count >= maxPerWindow) {
                return false;
            }
            count++;
            return true;
        }
    }
}
