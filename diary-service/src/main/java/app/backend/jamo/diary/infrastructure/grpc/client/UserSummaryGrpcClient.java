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
 * 응답 schema 자체에 fallback 이 표현 — 일시 장애 시 사용자에게 "(unknown)" 노출이 자연스러움. Circuit Breaker
 * 도입은 운영 모니터링 후 (예: identity-service 일시 장애 빈발 시).
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
