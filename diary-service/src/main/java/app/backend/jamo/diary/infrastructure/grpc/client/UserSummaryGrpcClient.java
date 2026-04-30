package app.backend.jamo.diary.infrastructure.grpc.client;

import app.backend.jamo.contracts.proto.identity.BatchGetUserSummariesRequest;
import app.backend.jamo.contracts.proto.identity.BatchGetUserSummariesResponse;
import app.backend.jamo.contracts.proto.identity.GetUserSummaryRequest;
import app.backend.jamo.contracts.proto.identity.GetUserSummaryResponse;
import app.backend.jamo.contracts.proto.identity.UserSummary;
import app.backend.jamo.contracts.proto.identity.UserSummaryServiceGrpc;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * identity-service 의 {@code UserSummaryService} gRPC 어댑터 — {@link UserSummaryPort} 구현.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §4 (응답 schema authorDisplayName) + identity.proto JavaDoc
 * (Deadline: GetUserSummary 2s / BatchGetUserSummaries 5s).
 *
 * <p><b>실패 정책</b> — read-only 가벼운 호출이라 NOT_FOUND / 일시 실패 모두 단순 fallback (Application Service
 * 가 {@code "(unknown)"} 처리):
 * <ul>
 *   <li>NOT_FOUND → empty 반환 (정상 흐름)</li>
 *   <li>DEADLINE_EXCEEDED / UNAVAILABLE / 기타 status → empty 반환 + log.warn (운영 모니터링)</li>
 *   <li>다른 unchecked 예외 → empty 반환 + log.warn</li>
 * </ul>
 *
 * <p><b>Resilience4j Circuit Breaker / Retry 미적용 (현 PR 시점)</b>: sentence-feedback 의
 * {@code ChatServiceSentenceFeedbackGatewayAdapter} 정합 적용은 후속. 본 어댑터는 read-only 가벼운 호출 +
 * 응답 schema 자체에 fallback 이 표현 — 일시 장애 시 사용자에게 "(unknown)" 노출이 자연스러움.
 *
 * <p><b>도입 트리거 박제</b> (cleanup PR — code-reviewer H2, PR #79 retrospective):
 * <ul>
 *   <li>identity-service 의 본 어댑터 호출 failure rate ≥ 5% (운영 모니터링 SLO)</li>
 *   <li>p99 latency ≥ deadline 의 80% (GET 1.6s / BATCH 4s) 지속 ≥ 30분</li>
 *   <li>cascading 장애 (UserSummary 미응답 → diary 응답 timeout 누적) 1회 이상</li>
 * </ul>
 * 위 트리거 발생 또는 <b>2026-Q3</b> 만료일 도달 시 둘 중 빠른 시점 — Resilience4j CircuitBreaker
 * (sliding-window-size 100, failure-rate-threshold 50%) + Retry (3회, exponential backoff 100ms→400ms) +
 * Bulkhead (semaphore 50) 도입. 도입 PR 에서 본 javadoc 갱신 + 본 어댑터 단위 테스트 보강.
 *
 * <p><b>측정 (현 PR 미적용)</b> — code-reviewer M4: 트리거 측정의 전제는 Micrometer Timer 계측. metric
 * 이름 후보 — {@code diary.user_summary.grpc.duration{op=get|batch,outcome=ok|fail}} (Timer) +
 * {@code diary.user_summary.grpc.failure_rate} (gauge — 5분 sliding). Resilience4j 도입 PR 에서 본 어댑터에
 * Micrometer 통합도 함께 추가 (Resilience4j 가 자동 노출하는 metric 와 별도로 어댑터 진입/실패 카운터).
 */
@Component
@Slf4j
public class UserSummaryGrpcClient implements UserSummaryPort {

    private static final long GET_DEADLINE_MS = 2_000L;
    private static final long BATCH_DEADLINE_MS = 5_000L;

    @GrpcClient("identity-service")
    private UserSummaryServiceGrpc.UserSummaryServiceBlockingStub stub;

    @Override
    public Optional<UserSummaryView> get(UUID userId) {
        try {
            GetUserSummaryResponse response = stub
                .withDeadlineAfter(GET_DEADLINE_MS, TimeUnit.MILLISECONDS)
                .getUserSummary(GetUserSummaryRequest.newBuilder()
                    .setUserId(userId.toString())
                    .build());
            return toViewIfOk(response);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            log.warn("UserSummary GET failed: status={} userId={}", e.getStatus().getCode(), userId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("UserSummary GET unexpected error: userId={}", userId, e);
            return Optional.empty();
        }
    }

    @Override
    public Map<UUID, UserSummaryView> batchGet(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            BatchGetUserSummariesResponse response = stub
                .withDeadlineAfter(BATCH_DEADLINE_MS, TimeUnit.MILLISECONDS)
                .batchGetUserSummaries(BatchGetUserSummariesRequest.newBuilder()
                    .addAllUserIds(userIds.stream().map(UUID::toString).collect(Collectors.toList()))
                    .build());

            Map<UUID, UserSummaryView> result = new HashMap<>(response.getSummariesCount());
            for (GetUserSummaryResponse item : response.getSummariesList()) {
                toViewIfOk(item).ifPresent(view -> result.put(view.userId(), view));
            }
            return result;
        } catch (StatusRuntimeException e) {
            log.warn("UserSummary BATCH failed: status={} size={}", e.getStatus().getCode(), userIds.size());
            return Map.of();
        } catch (RuntimeException e) {
            log.warn("UserSummary BATCH unexpected error: size={}", userIds.size(), e);
            return Map.of();
        }
    }

    /** 응답의 body status 가 "OK" 일 때만 view 반환 — "NOT_FOUND" / "FAILED" 는 empty. */
    private static Optional<UserSummaryView> toViewIfOk(GetUserSummaryResponse response) {
        if (!"OK".equals(response.getStatus())) {
            return Optional.empty();
        }
        return toView(response.getSummary());
    }

    private static Optional<UserSummaryView> toView(UserSummary summary) {
        if (summary.getUserId().isBlank()) {
            return Optional.empty();
        }
        UUID userId;
        try {
            userId = UUID.fromString(summary.getUserId());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String displayName = summary.getDisplayName().isBlank()
            ? UserSummaryView.UNKNOWN_DISPLAY_NAME
            : summary.getDisplayName();
        return Optional.of(new UserSummaryView(userId, displayName));
    }
}
