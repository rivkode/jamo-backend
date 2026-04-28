package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.domain.model.oauth.OAuthIdentity;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.oauth.OAuthUserInfo;
import app.backend.jamo.identity.domain.model.user.DisplayName;
import app.backend.jamo.identity.domain.model.user.DisplayNameTruncation;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.repository.OAuthIdentityRepository;
import app.backend.jamo.identity.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * OAuth provider 의 사용자 정보로 User 를 찾거나 새로 등록.
 *
 * <p>핵심 정책 (ADR-0006 결정 4): {@code (provider, providerUserId)} 만 매칭 키.
 * email 이 다른 User 와 중복돼도 자동 링크하지 않고 새 User 로 등록 — MSA 환경의
 * OAuth account hijacking via email collision 회피.
 *
 * <p>본 service 는 Application 계층에 위치 — DB 트랜잭션 경계를 직접 보유 ({@code @Transactional}).
 * {@link OAuthCallbackService} 가 외부 IO (provider HTTP) 후 본 service 호출 시 트랜잭션이
 * 짧게 유지되도록 설계 (security review H1 → 분리).
 *
 * <p><b>호출자 제약</b>: 본 service 는 {@link OAuthCallbackService} 의 트랜잭션 경계 격리
 * 전용 implementation detail 이며, 다른 use case 의 직접 진입점이 아니다. 새 호출자가
 * 필요해지면 별도 use case service 로 분리할 것. (code review M1)
 */
@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final OAuthIdentityRepository oauthIdentityRepository;

    @Transactional
    public UserRegistrationResult findOrRegister(OAuthProvider provider,
                                                 OAuthUserInfo info,
                                                 Instant now) {
        Optional<OAuthIdentity> existing =
                oauthIdentityRepository.findByProviderAndProviderUserId(provider, info.providerUserId());
        if (existing.isPresent()) {
            User user = userRepository.findById(existing.get().userId())
                    .orElseThrow(() -> new IllegalStateException(
                            "oauth identity references missing user: " + existing.get().userId()));
            return new UserRegistrationResult(user, false, false);
        }

        DisplayNameTruncation truncation = DisplayName.truncated(info.rawNickname());
        Email email = info.email().orElse(null);
        User created = User.registerWithOAuth(
                provider,
                info.providerUserId(),
                truncation.displayName(),
                email,
                now
        );
        User saved = userRepository.save(created);
        return new UserRegistrationResult(saved, true, truncation.wasTruncated());
    }
}
