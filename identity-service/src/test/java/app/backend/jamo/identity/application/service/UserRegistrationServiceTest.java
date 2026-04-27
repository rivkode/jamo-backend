package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.domain.model.oauth.OAuthIdentity;
import app.backend.jamo.identity.domain.model.oauth.OAuthIdentityId;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.oauth.OAuthUserInfo;
import app.backend.jamo.identity.domain.model.oauth.ProviderUserId;
import app.backend.jamo.identity.domain.model.user.AccountType;
import app.backend.jamo.identity.domain.model.user.DisplayName;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.domain.repository.OAuthIdentityRepository;
import app.backend.jamo.identity.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-27T10:00:00Z");
    private static final ProviderUserId PROVIDER_USER_ID = new ProviderUserId("kakao-1");

    private UserRepository userRepository;
    private OAuthIdentityRepository oauthIdentityRepository;
    private UserRegistrationService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        oauthIdentityRepository = mock(OAuthIdentityRepository.class);
        service = new UserRegistrationService(userRepository, oauthIdentityRepository);
    }

    @Test
    void registers_new_user_when_oauth_identity_not_found() {
        when(oauthIdentityRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        OAuthUserInfo info = OAuthUserInfo.of(PROVIDER_USER_ID, "jamo-user", new Email("u@k.com"));

        UserRegistrationResult result = service.findOrRegister(OAuthProvider.KAKAO, info, NOW);

        assertThat(result.isNewUser()).isTrue();
        assertThat(result.displayNameTruncated()).isFalse();
        assertThat(result.user().displayName().value()).isEqualTo("jamo-user");
        assertThat(result.user().email()).contains(new Email("u@k.com"));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void truncates_display_name_when_raw_nickname_exceeds_limit() {
        when(oauthIdentityRepository.findByProviderAndProviderUserId(any(), any()))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        OAuthUserInfo info = OAuthUserInfo.withoutEmail(PROVIDER_USER_ID, "a".repeat(50));

        UserRegistrationResult result = service.findOrRegister(OAuthProvider.GOOGLE, info, NOW);

        assertThat(result.displayNameTruncated()).isTrue();
        assertThat(result.user().displayName().value()).hasSize(32);
    }

    @Test
    void registers_new_user_without_email_when_provider_did_not_supply() {
        when(oauthIdentityRepository.findByProviderAndProviderUserId(any(), any()))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        OAuthUserInfo info = OAuthUserInfo.withoutEmail(PROVIDER_USER_ID, "jamo");

        UserRegistrationResult result = service.findOrRegister(OAuthProvider.NAVER, info, NOW);

        assertThat(result.user().email()).isEmpty();
    }

    @Test
    void returns_existing_user_when_oauth_identity_found() {
        UserId existingUserId = UserId.generate();
        OAuthIdentity identity = OAuthIdentity.restore(
                OAuthIdentityId.generate(), existingUserId, OAuthProvider.KAKAO,
                PROVIDER_USER_ID, NOW.minusSeconds(86400));
        User existingUser = User.restore(
                existingUserId, new DisplayName("existing"), new Email("e@k.com"),
                AccountType.OAUTH, null,
                NOW.minusSeconds(86400), NOW.minusSeconds(86400), List.of(identity));

        when(oauthIdentityRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(Optional.of(identity));
        when(userRepository.findById(existingUserId)).thenReturn(Optional.of(existingUser));

        OAuthUserInfo info = OAuthUserInfo.of(PROVIDER_USER_ID, "jamo-changed", new Email("changed@k.com"));

        UserRegistrationResult result = service.findOrRegister(OAuthProvider.KAKAO, info, NOW);

        assertThat(result.isNewUser()).isFalse();
        assertThat(result.displayNameTruncated()).isFalse();
        assertThat(result.user()).isSameAs(existingUser);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void does_not_link_when_email_already_exists_for_different_user() {
        // ADR-0006 결정 4 enforcement: email 중복 시에도 새 User 등록
        // (provider+providerUserId 가 SoT — email 매칭 코드 자체가 없음)
        when(oauthIdentityRepository.findByProviderAndProviderUserId(any(), any()))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // duplicate@example.com 는 다른 User 가 이미 가지고 있다고 가정 — 그러나 우리는 조회 자체를 안 함
        OAuthUserInfo info = OAuthUserInfo.of(
                PROVIDER_USER_ID, "jamo", new Email("duplicate@example.com"));

        UserRegistrationResult result = service.findOrRegister(OAuthProvider.GOOGLE, info, NOW);

        assertThat(result.isNewUser()).isTrue();
        assertThat(result.user().email()).contains(new Email("duplicate@example.com"));
        // 핵심 검증: email 로 user 조회를 시도조차 하지 않음
        verify(userRepository, never()).findById(any());
    }

    @Test
    void throws_when_oauth_identity_references_missing_user() {
        OAuthIdentity orphan = OAuthIdentity.restore(
                OAuthIdentityId.generate(), UserId.generate(), OAuthProvider.KAKAO,
                PROVIDER_USER_ID, NOW);
        when(oauthIdentityRepository.findByProviderAndProviderUserId(any(), any()))
                .thenReturn(Optional.of(orphan));
        when(userRepository.findById(orphan.userId())).thenReturn(Optional.empty());

        OAuthUserInfo info = OAuthUserInfo.withoutEmail(PROVIDER_USER_ID, "jamo");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.findOrRegister(OAuthProvider.KAKAO, info, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing user");
    }
}
