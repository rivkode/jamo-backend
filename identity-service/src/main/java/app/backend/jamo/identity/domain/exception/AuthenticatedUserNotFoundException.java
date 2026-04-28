package app.backend.jamo.identity.domain.exception;

/**
 * 인증된 사용자 (`@LoginUser` 가 검증한 userId) 가 DB 에 없을 때 발생.
 *
 * <p>발급된 access JWT 의 userId 가 DB 에 부재 = **시스템 invariant 위반** (auth flow 가 토큰을
 * 발급한 user 가 정상적으로 존재하지 않는 상태). 입력 검증 실패가 아니라 서버 측 일관성 깨짐이므로
 * presentation 매핑은 **500 INTERNAL_ERROR** (PR6-c, 결정 박제 — `/me` 경로의 5xx 시그널).
 *
 * <p>{@link UserNotFoundException} 와 분리한 이유: 같은 *user not found* 도 *호출 맥락에 따라*
 * 의미가 다름. 본인 조회 (`/me`) 는 5xx, 타인 조회 (`/{userId}`) 는 404 — code-reviewer M1 후속 박제.
 *
 * <p>발생 경로:
 * <ul>
 *   <li>{@code RetrieveMyProfileService.retrieve(query)} — `/me` GET</li>
 *   <li>{@code UpdateMyProfileService.update(command)} — `/me` PATCH</li>
 * </ul>
 */
public class AuthenticatedUserNotFoundException extends RuntimeException {

    public AuthenticatedUserNotFoundException(String message) {
        super(message);
    }
}
