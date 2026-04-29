package app.backend.jamo.diary.application.port;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * identity-service 의 {@code UserSummaryService} (PR #35) gRPC 호출 추상화 port.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §4 (응답 schema 의 authorDisplayName 조립).
 *
 * <p><b>책임 분리</b>: Application 은 본 port 의존, Infrastructure 의 gRPC 어댑터가 Resilience4j Circuit
 * Breaker / Retry / Deadline (UserSummary 는 read-only 가벼운 호출 — 권장 2초) 적용. 다른 서비스 (identity)
 * Aggregate 직접 의존 금지 — gRPC 만 통과 (CLAUDE.md "한 서비스 = 하나의 BC").
 *
 * <p>실패 정책 (Infrastructure 어댑터 책임):
 * <ul>
 *   <li>일시 실패 (DEADLINE_EXCEEDED / UNAVAILABLE) — Retry 후에도 실패 시 fallback (displayName=null 또는 "(unknown)")</li>
 *   <li>NOT_FOUND — Optional.empty 반환 (단건) / Map 에서 누락 (Batch)</li>
 * </ul>
 *
 * <p>fallback 처리 정책 (display 영역) 은 Application Service 가 결정 — 본 port 는 단순 조회.
 */
public interface UserSummaryPort {

    /**
     * 단건 조회 — 일기 작성/단건 조회 (CreateDiary / GetDiary) 시 사용.
     *
     * @return 조회 결과 — NOT_FOUND 시 empty
     */
    Optional<UserSummaryView> get(UUID userId);

    /**
     * 일괄 조회 — 피드 (ListPublicFeed / ListMyFeed) 응답 조립 시 N+1 회피.
     *
     * <p>최대 200건 (identity-service Batch API 제한, PR #35). 호출자가 페이지 크기 (max 100) 기준 사용.
     *
     * @return userId → UserSummaryView. NOT_FOUND 인 userId 는 누락 (호출자가 fallback 결정)
     */
    Map<UUID, UserSummaryView> batchGet(Set<UUID> userIds);
}
