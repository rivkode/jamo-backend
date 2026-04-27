package app.backend.jamo.identity.domain.repository;

import app.backend.jamo.identity.domain.model.auth.RefreshTokenRecord;
import app.backend.jamo.identity.domain.model.user.UserId;

import java.util.Optional;
import java.util.Set;

/**
 * Refresh JWT hash 보관소 (port).
 *
 * <p>회전형 refresh 정책 (PRD auth/refresh.md §9): 매 호출 시 신규 sessionId 로 store,
 * 구 sessionId 는 즉시 {@link #delete}. 폐기된 sid 의 refresh 가 재사용되면 application 은
 * {@link #findAllSessionIds} 로 user 의 모든 sid 를 수집해 일괄 blacklist + 삭제 (보상 트랜잭션).
 */
public interface RefreshTokenStore {

    void store(RefreshTokenRecord record);

    Optional<RefreshTokenRecord> find(UserId userId, String sessionId);

    void delete(UserId userId, String sessionId);

    /**
     * 해당 user 가 보유한 active sessionId 전체 반환.
     * reuse detection 시 모든 sid 를 blacklist 등록하고 삭제하기 위한 보조 인덱스 조회.
     * 일치하는 sid 가 없으면 빈 Set.
     */
    Set<String> findAllSessionIds(UserId userId);
}
