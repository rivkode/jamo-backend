package app.backend.jamo.diary.presentation.web;

import app.backend.jamo.diary.application.dto.diarychat.PollView;
import app.backend.jamo.diary.application.service.diarychat.PollMessagesService;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.presentation.dto.diarychat.PollResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 롱폴(poll) DeferredResult orchestration — servlet thread 를 점유하지 않고 스케줄러로 주기 체크.
 *
 * <p>박제 v2 §8-b: 접근 검증 + baseline 캡처는 <b>요청 thread 에서 동기</b>로 수행({@code beginPoll} 예외 →
 * ExceptionHandler 가 404). 이후 {@link #INTERVAL_MS} 간격으로 {@code pollOnce} 를 돌려 데이터 발생 시 즉시
 * 완료, 없으면 {@code wait} 초 timeout 에 빈 결과. 완료/만료 시 스케줄 future 취소.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatPollingCoordinator {

    private static final long INTERVAL_MS = 700;

    private final PollMessagesService pollService;
    private final ScheduledExecutorService chatPollScheduler;

    public DeferredResult<PollResponse> poll(RoomId roomId, long after, int waitSeconds, UUID requester) {
        long baseline = pollService.beginPoll(roomId, requester);  // 동기 접근 검증 (예외 → 404)
        long timeoutMs = Math.max(1, waitSeconds) * 1000L;
        DeferredResult<PollResponse> deferred =
            new DeferredResult<>(timeoutMs, PollResponse.from(PollView.empty(after)));

        PollView immediate = pollService.pollOnce(roomId, after, baseline);
        if (immediate.hasData()) {
            deferred.setResult(PollResponse.from(immediate));
            return deferred;
        }

        ScheduledFuture<?>[] future = new ScheduledFuture<?>[1];
        future[0] = chatPollScheduler.scheduleWithFixedDelay(() -> {
            try {
                if (deferred.isSetOrExpired()) {
                    cancel(future);
                    return;
                }
                PollView v = pollService.pollOnce(roomId, after, baseline);
                if (v.hasData()) {
                    deferred.setResult(PollResponse.from(v));
                    cancel(future);
                }
            } catch (Exception ex) {
                // 일시 read 실패(DB 흔들림 등)는 폴 전체를 깨지 않고 이번 회차만 건너뛴다 — 다음 주기 재시도,
                // 끝내 데이터 없으면 timeout 빈 결과로 정상 종료 (롱폴 semantics, code-reviewer H2).
                log.warn("chat poll check failed roomId={}: {}", roomId.value(), ex.toString());
            }
        }, INTERVAL_MS, INTERVAL_MS, TimeUnit.MILLISECONDS);

        deferred.onCompletion(() -> cancel(future));
        return deferred;
    }

    private void cancel(ScheduledFuture<?>[] future) {
        if (future[0] != null) {
            future[0].cancel(false);
        }
    }
}
