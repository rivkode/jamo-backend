package app.backend.jamo.identity.domain.model.user;

/**
 * 사용자가 처음 가입한 자격증명 경로 (PRD user/createUser.md §9, ADR-0006).
 *
 * <p>{@link User#hashedPassword()} 의 nullable 여부와 1:1 대응한다.
 * <ul>
 *   <li>{@link #LOCAL}: email + password 로 직접 가입. {@code hashedPassword} 필수.</li>
 *   <li>{@link #OAUTH}: provider 로그인으로 가입. {@code hashedPassword} 없음, {@code oauthIdentities} 1개 이상.</li>
 * </ul>
 *
 * <p>중복 검증 ({@code existsLocalAccountByEmail}) 의 식별 키. {@link app.backend.jamo.identity.domain.model.oauth.OAuthProvider}
 * 와 의미 분리: {@code OAuthProvider} 는 OAuth provider 한정 (KAKAO/NAVER/GOOGLE),
 * {@code AccountType} 은 가입 경로 (LOCAL/OAUTH) — ADR-0006 결정 4 의 OAuth-only enum 의미를 침범하지 않음.
 */
public enum AccountType {
    LOCAL,
    OAUTH
}
