package app.backend.jamo.identity.domain.exception;

/**
 * Profile aggregate 조회 실패 — PRD `profile/getProfile.md` §5 / `profile/updateMyProfile.md` §3.
 *
 * <p>주로 다음 경로에서 발생:
 * <ul>
 *   <li>{@code RetrieveProfileService.retrieve(loginUserId, userId)} 의 path userId 가 존재하지 않음.</li>
 *   <li>{@code UpdateMyProfileService.update(...)} 의 본인 Profile 이 없음 — 회원가입 시 자동 생성
 *       정책이 제대로 적용 안 됐거나 Profile 생성 시점이 lazy 인 경우 (Phase 6-b-b 결정 후속).</li>
 * </ul>
 *
 * <p>presentation 매핑: 404 {@code USER_NOT_FOUND} (Profile 의 부재 = 사용자 부재로 해석 — shared
 * identifier 패턴이라 Profile 만 단독으로 없는 정상 상태가 정의되지 않음).
 */
public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException(String message) {
        super(message);
    }
}
