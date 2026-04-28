package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.MyProfileResult;
import app.backend.jamo.identity.application.dto.RetrieveMyProfileQuery;
import app.backend.jamo.identity.domain.exception.AuthenticatedUserNotFoundException;
import app.backend.jamo.identity.domain.model.oauth.OAuthIdentity;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.oauth.ProviderUserId;
import app.backend.jamo.identity.domain.model.profile.AvatarUrl;
import app.backend.jamo.identity.domain.model.profile.Bio;
import app.backend.jamo.identity.domain.model.profile.Locale;
import app.backend.jamo.identity.domain.model.profile.Profile;
import app.backend.jamo.identity.domain.model.user.DisplayName;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.domain.repository.ProfileRepository;
import app.backend.jamo.identity.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetrieveMyProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-28T10:00:00Z");

    private UserRepository userRepository;
    private ProfileRepository profileRepository;
    private RetrieveMyProfileService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        profileRepository = mock(ProfileRepository.class);
        service = new RetrieveMyProfileService(userRepository, profileRepository);
    }

    @Test
    void retrieve_returns_user_identity_and_profile_fields() {
        UserId userId = UserId.generate();
        User user = User.registerWithOAuth(
                OAuthProvider.GOOGLE, new ProviderUserId("google-1"),
                new DisplayName("jamo"), new Email("user@jamoai.app"), NOW);
        Profile profile = Profile.restore(userId, new Bio("hello"),
                new AvatarUrl("https://e.io/a.png"), new Locale("en"), NOW, NOW);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

        MyProfileResult result = service.retrieve(new RetrieveMyProfileQuery(userId));

        assertThat(result.id()).isEqualTo(user.id());
        assertThat(result.email()).isEqualTo(new Email("user@jamoai.app"));
        assertThat(result.displayName()).isEqualTo(new DisplayName("jamo"));
        assertThat(result.providers()).containsExactly(OAuthProvider.GOOGLE);
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.bio()).isEqualTo(new Bio("hello"));
        assertThat(result.avatarUrl()).isEqualTo(new AvatarUrl("https://e.io/a.png"));
        assertThat(result.locale()).isEqualTo(new Locale("en"));
    }

    @Test
    void retrieve_returns_default_locale_when_profile_missing() {
        // Lazy + Read 기본값 패턴 — Profile 부재 시 기본값 합성, DB write 없음
        UserId userId = UserId.generate();
        User user = User.registerWithOAuth(
                OAuthProvider.KAKAO, new ProviderUserId("kakao-1"),
                new DisplayName("nick"), new Email("k@jamoai.app"), NOW);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());

        MyProfileResult result = service.retrieve(new RetrieveMyProfileQuery(userId));

        assertThat(result.bio()).isNull();
        assertThat(result.avatarUrl()).isNull();
        assertThat(result.locale()).isEqualTo(Locale.DEFAULT);  // "ko"
    }

    @Test
    void retrieve_throws_when_user_not_found() {
        UserId userId = UserId.generate();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retrieve(new RetrieveMyProfileQuery(userId)))
                .isInstanceOf(AuthenticatedUserNotFoundException.class);
    }

    @Test
    void retrieve_returns_email_null_when_user_email_absent() {
        // OAuth 가입자 중 IdP 가 email 미공개 → User.registerWithOAuth 의 email 파라미터 null 허용
        User user = User.registerWithOAuth(
                OAuthProvider.GOOGLE, new ProviderUserId("g-1"),
                new DisplayName("anon"), null, NOW);
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));
        when(profileRepository.findById(user.id())).thenReturn(Optional.empty());

        MyProfileResult result = service.retrieve(new RetrieveMyProfileQuery(user.id()));

        assertThat(result.email()).isNull();
        assertThat(result.providers()).containsExactly(OAuthProvider.GOOGLE);
    }

    @Test
    void retrieve_maps_oauth_identities_to_provider_list() {
        User user = User.registerWithOAuth(
                OAuthProvider.GOOGLE, new ProviderUserId("google-1"),
                new DisplayName("jamo"), new Email("u@jamoai.app"), NOW);
        user.linkOAuth(OAuthProvider.KAKAO, new ProviderUserId("kakao-1"), NOW);

        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));
        when(profileRepository.findById(user.id())).thenReturn(Optional.empty());

        MyProfileResult result = service.retrieve(new RetrieveMyProfileQuery(user.id()));

        assertThat(result.providers()).containsExactlyInAnyOrder(OAuthProvider.GOOGLE, OAuthProvider.KAKAO);
    }
}
