package app.backend.jamo.identity.domain.repository;

import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.model.user.UserId;

import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId id);

    /**
     * LOCAL 가입 한정 이메일 중복 검사 (PRD user/createUser.md §9 FIX 항목).
     *
     * <p>{@code account_type = LOCAL AND email = ?} 일치 row 가 1건 이상이면 true.
     * OAuth 가입자와의 email 충돌은 본 검사 범위 밖 (ADR-0006 결정 4: OAuth 자동 링크 X).
     */
    boolean existsLocalAccountByEmail(Email email);

    /**
     * LOCAL email/password 로그인 전용 조회.
     *
     * <p>OAuth 계정과 email 이 같아도 자동 링크하지 않는 ADR-0006 정책을 유지하기 위해
     * {@code account_type = LOCAL} row 만 반환한다.
     */
    Optional<User> findLocalAccountByEmail(Email email);
}
