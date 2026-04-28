package app.backend.jamo.identity.domain.model.profile;

import java.util.Objects;
import java.util.Set;

/**
 * Profile 의 사용자 UI 언어 VO — ISO 639-1 화이트리스트.
 *
 * <p>PRD `profile/updateMyProfile.md` §1 — `ko` / `en` 화이트리스트, 추후 운영 결정으로 확장.
 * 표준 {@code java.util.Locale} 과 이름 충돌 — 본 도메인 VO 는 *우리 서비스가 지원하는 언어 코드*
 * 라는 더 좁은 의미라 별도 VO 로 모델링 (decisions/identity/profile-prd-evaluation.md §결과및영향 #Domain).
 *
 * <p>기본값 {@link #DEFAULT}({@code "ko"}) 는 Profile.create 시 사용 — 회원가입 후 사용자가
 * 명시적으로 변경하기 전까지 적용.
 */
public record Locale(String code) {

    private static final Set<String> ALLOWED = Set.of("ko", "en");

    public static final Locale DEFAULT = new Locale("ko");

    public Locale {
        Objects.requireNonNull(code, "code");
        if (!ALLOWED.contains(code)) {
            throw new IllegalArgumentException("unsupported locale: " + code + " (allowed: " + ALLOWED + ")");
        }
    }

    public static Set<String> allowed() {
        return ALLOWED;
    }
}
