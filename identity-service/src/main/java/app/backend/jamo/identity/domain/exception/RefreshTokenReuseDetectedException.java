package app.backend.jamo.identity.domain.exception;

/**
 * 이미 회전(rotation) 으로 폐기된 sessionId 의 refresh JWT 가 재사용된 경우 발생.
 *
 * <p>탈취 가능성을 시사하므로 application layer 는 본 예외를 보상 트랜잭션 트리거로 사용한다 —
 * 해당 user 의 모든 sessionId blacklist 등록 + RefreshTokenStore 일괄 삭제
 * (PRD auth/refresh.md §9 — reuse detection).
 *
 * <p>일반 검증 실패({@link RefreshTokenInvalidException}) 와 분리해 보안 메트릭/알람을
 * 독립적으로 부착할 수 있도록 한다.
 */
public class RefreshTokenReuseDetectedException extends RefreshTokenException {

    public RefreshTokenReuseDetectedException(String message) {
        super(message);
    }
}
