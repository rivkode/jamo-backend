package app.backend.jamo.identity.domain.exception;

/**
 * displayName 변경 빈도 제한 (7일 1회) 위반 — PRD `profile/updateMyProfile.md` §5,
 * decisions/identity/profile-prd-evaluation.md §결정 #3.1.
 *
 * <p>{@code DisplayNameChangeRateLimiter.check(userId)} 가 flag 존재를 감지하면 던진다.
 * presentation 매핑은 400 {@code DISPLAY_NAME_CHANGE_TOO_FREQUENT} — 메시지에는 남은 시간 등
 * 구체 정보 미포함 (운영 단순화).
 */
public class DisplayNameChangeTooFrequentException extends RuntimeException {

    public DisplayNameChangeTooFrequentException(String message) {
        super(message);
    }
}
