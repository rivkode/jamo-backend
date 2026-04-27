package app.backend.jamo.identity.domain.repository;

import app.backend.jamo.identity.domain.model.auth.AuthState;
import app.backend.jamo.identity.domain.model.auth.OAuthFlowSession;

import java.util.Optional;

/**
 * OAuth 흐름의 state 단위 보관소 (port).
 *
 * <ul>
 *   <li>{@link #store} 는 TTL 적용 (state cookie max-age 와 동일).</li>
 *   <li>{@link #consume} 은 atomic GETDEL — 동일 state 를 두 번 사용할 수 없도록 보장 (state replay 차단).</li>
 * </ul>
 */
public interface OAuthFlowSessionStore {

    void store(OAuthFlowSession session);

    Optional<OAuthFlowSession> consume(AuthState state);
}
