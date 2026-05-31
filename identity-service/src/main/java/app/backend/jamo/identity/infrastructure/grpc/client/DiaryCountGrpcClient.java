package app.backend.jamo.identity.infrastructure.grpc.client;

import app.backend.jamo.contracts.proto.diary.DiaryQueryServiceGrpc;
import app.backend.jamo.contracts.proto.diary.GetDiaryCountRequest;
import app.backend.jamo.contracts.proto.diary.GetDiaryCountResponse;
import app.backend.jamo.identity.application.port.DiaryCountPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * diary-service 의 {@code DiaryQueryService} gRPC 어댑터 — {@link DiaryCountPort} 구현 (Slice 3-b).
 *
 * <p>박제: contracts/diary.proto + plan §5 S3-b (Resilience4j 적용 + 실패 시 diaryCount=null).
 *
 * <p>적용 정책 (CLAUDE.md NEVER):
 * <ul>
 *   <li><b>Deadline 5s</b> — {@code stub.withDeadlineAfter(5, SECONDS)} (단순 count + 마진). 매 retry
 *       시도마다 리셋 — maxAttempts=2 라 누적 최악 5s×2 + 200ms ≈ 10.2s (code-reviewer M1).</li>
 *   <li><b>Circuit Breaker</b> — {@code @CircuitBreaker(name="diaryService", fallbackMethod="fallback")}.</li>
 *   <li><b>Retry</b> — {@code @Retry(name="diaryService")} (gRPC StatusRuntimeException 만, 2회).</li>
 *   <li><b>실패 일원화 → null</b> — gRPC 시스템 오류 / Circuit OPEN / 응답 status="FAILED" 모두 {@code null}
 *       반환 (profile service 가 diaryCount=null 노출). 0 (일기 없음) 과 구분.</li>
 * </ul>
 *
 * <p>응답 body status="OK" 일 때만 count 반환. "FAILED" / 알 수 없는 status → null (fallback 과 동일 의미).
 */
@Component
@Slf4j
public class DiaryCountGrpcClient implements DiaryCountPort {

    private static final long DEADLINE_MS = 5_000L;
    private static final String STATUS_OK = "OK";

    private final DiaryQueryServiceGrpc.DiaryQueryServiceBlockingStub stub;

    public DiaryCountGrpcClient(
        @GrpcClient("diary-service") DiaryQueryServiceGrpc.DiaryQueryServiceBlockingStub stub
    ) {
        this.stub = stub;
    }

    @Override
    @CircuitBreaker(name = "diaryService", fallbackMethod = "fallback")
    @Retry(name = "diaryService")
    public Long getCount(UUID authorId, boolean includePrivate) {
        try {
            GetDiaryCountResponse response = stub
                .withDeadlineAfter(DEADLINE_MS, TimeUnit.MILLISECONDS)
                .getDiaryCount(GetDiaryCountRequest.newBuilder()
                    .setAuthorId(authorId.toString())
                    .setIncludePrivate(includePrivate)
                    .build());

            if (STATUS_OK.equals(response.getStatus())) {
                return response.getCount();
            }
            // status="FAILED" 등 — diary-service 내부 오류. null 일원화 (gRPC error 아니므로 retry 안 함).
            log.warn("GetDiaryCount returned non-OK status: status='{}' authorId={}",
                response.getStatus(), authorId);
            return null;
        } catch (StatusRuntimeException ex) {
            log.warn("diary-service GetDiaryCount gRPC failed: status={} authorId={}",
                ex.getStatus().getCode(), authorId);
            throw ex;  // @Retry 트리거 — 모든 시도 실패 후 fallback 호출
        }
    }

    /**
     * Resilience4j fallback — Circuit OPEN / 모든 retry 실패 후 호출. diaryCount=null 일원화.
     */
    @SuppressWarnings("unused")
    private Long fallback(UUID authorId, boolean includePrivate, Throwable ex) {
        log.warn("diary-service GetDiaryCount fallback authorId={} cause={}",
            authorId, ex.getClass().getSimpleName());
        return null;
    }
}
