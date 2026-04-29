package app.backend.jamo.diary.application.port;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UserSummary 조회 결과 — identity-service 의 UserSummaryService gRPC 응답 변환.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §4 (응답 schema 의 authorDisplayName).
 *
 * <p>{@code displayName} 은 사용자 표시명 (nickname). identity-service 의 profile 정합.
 */
public record UserSummaryView(UUID userId, String displayName) {

    /**
     * NOT_FOUND / 일시 장애 fallback — 모든 Application Service 가 동일 값으로 노출 (응답 schema 일관성).
     */
    public static final String UNKNOWN_DISPLAY_NAME = "(unknown)";

    public UserSummaryView {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(displayName, "displayName");
    }

    /**
     * Optional 결과를 displayName 으로 변환. NOT_FOUND 시 {@link #UNKNOWN_DISPLAY_NAME}. 4개 service 의 fallback
     * 코드 중복 회피.
     */
    public static String displayNameOrUnknown(Optional<UserSummaryView> view) {
        return view.map(UserSummaryView::displayName).orElse(UNKNOWN_DISPLAY_NAME);
    }
}
