package app.backend.jamo.identity.domain.model.profile;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * Profile 의 아바타 URL VO.
 *
 * <p>PRD `profile/updateMyProfile.md` §1 — http/https URL ≤500자. 0자/blank 는 VO 거부 —
 * Application 에서 ""→null 정규화 (아바타 제거, decisions/identity/profile-prd-evaluation.md §F2).
 *
 * <p>본 VO 는 *형식적* URL 검증만 수행 (URI 파싱 + scheme http/https). 실제 reachability /
 * content-type / 이미지 여부 검증은 본 슬라이스 범위 밖 — 후속 endpoint
 * (`POST /api/v1/profiles/me/avatar` 파일 업로드) 또는 별도 비동기 검증 도입 시 결정.
 */
public record AvatarUrl(String value) {

    public static final int MAX_LENGTH = 500;

    public AvatarUrl {
        Objects.requireNonNull(value, "value");
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException("avatarUrl must not be blank — use null to unset");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("avatarUrl length out of range: max " + MAX_LENGTH);
        }
        // CRLF / control character 차단 (Log/Header injection 방어, security review M1)
        if (value.chars().anyMatch(c -> c < 0x20 || c == 0x7F)) {
            throw new IllegalArgumentException("avatarUrl must not contain control characters");
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("invalid avatarUrl: " + e.getMessage());
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("avatarUrl scheme must be http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("avatarUrl host must be present");
        }
        // userInfo (https://user:pass@host/) 차단 — phishing / credential leak 위험
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("avatarUrl must not contain userInfo");
        }
    }
}
