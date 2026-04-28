package app.backend.jamo.identity.domain.model.profile;

import java.util.Objects;

/**
 * Profile 의 자기소개 VO.
 *
 * <p>PRD `profile/updateMyProfile.md` §1 화이트리스트 — 1-200자 (record invariant). 0자/blank 는
 * VO 의미 약화 회피로 거부 — Application 에서 ""→null 정규화 (decisions/identity/profile-prd-evaluation.md §F2).
 * "bio 미설정" 상태는 Profile aggregate 의 `bio` 필드 null 로 표현.
 */
public record Bio(String value) {

    public static final int MAX_LENGTH = 200;

    public Bio {
        Objects.requireNonNull(value, "value");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("bio must not be blank — use null to unset");
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("bio length out of range: max " + MAX_LENGTH);
        }
        value = normalized;  // canonical form — 양 끝 공백 제거 후 저장
    }
}
