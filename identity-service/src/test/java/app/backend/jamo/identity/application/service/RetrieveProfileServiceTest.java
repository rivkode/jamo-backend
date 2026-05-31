package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.PublicProfileResult;
import app.backend.jamo.identity.application.dto.RetrieveProfileQuery;
import app.backend.jamo.identity.application.port.DiaryCountPort;
import app.backend.jamo.identity.domain.exception.UserNotFoundException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrieveProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-28T10:00:00Z");

    private UserRepository userRepository;
    private ProfileRepository profileRepository;
    private DiaryCountPort diaryCountPort;
    private RetrieveProfileService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        profileRepository = mock(ProfileRepository.class);
        diaryCountPort = mock(DiaryCountPort.class);
        service = new RetrieveProfileService(userRepository, profileRepository, diaryCountPort);
    }

    @Test
    void retrieve_returns_public_safe_4_fields_only() {
        UserId targetUserId = UserId.generate();
        User user = User.registerWithOAuth(
                OAuthProvider.GOOGLE, new ProviderUserId("g-1"),
                new DisplayName("nick"), new Email("u@jamoai.app"), NOW);
        Profile profile = Profile.restore(targetUserId, new Bio("hi"),
                new AvatarUrl("https://e.io/a.png"), new Locale("en"), NOW, NOW);

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));
        when(profileRepository.findById(targetUserId)).thenReturn(Optional.of(profile));
        when(diaryCountPort.getCount(eq(targetUserId.value()), eq(false))).thenReturn(3L);

        PublicProfileResult result = service.retrieve(new RetrieveProfileQuery(targetUserId));

        assertThat(result.id()).isEqualTo(user.id());
        assertThat(result.displayName()).isEqualTo(new DisplayName("nick"));
        assertThat(result.bio()).isEqualTo(new Bio("hi"));
        assertThat(result.avatarUrl()).isEqualTo(new AvatarUrl("https://e.io/a.png"));
        assertThat(result.diaryCount()).isEqualTo(3L);
        // 타인 조회 — 공개 일기만 (includePrivate=false, IDOR 차단)
        verify(diaryCountPort).getCount(targetUserId.value(), false);
        // public-safe: email / providers / createdAt / locale 노출 X — record 에 필드 자체가 없음
    }

    @Test
    void retrieve_returns_null_optional_fields_when_profile_missing() {
        UserId targetUserId = UserId.generate();
        User user = User.registerWithOAuth(
                OAuthProvider.KAKAO, new ProviderUserId("k-1"),
                new DisplayName("anon"), new Email("u@jamoai.app"), NOW);

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));
        when(profileRepository.findById(targetUserId)).thenReturn(Optional.empty());

        PublicProfileResult result = service.retrieve(new RetrieveProfileQuery(targetUserId));

        assertThat(result.bio()).isNull();
        assertThat(result.avatarUrl()).isNull();
        assertThat(result.displayName()).isEqualTo(new DisplayName("anon"));
    }

    @Test
    void retrieve_returns_null_diaryCount_when_grpc_fails() {
        // 타인 조회 IDOR 경로 (includePrivate=false) 에서 gRPC 실패 시 fallback=null 노출 (test-reviewer M1).
        UserId targetUserId = UserId.generate();
        User user = User.registerWithOAuth(
                OAuthProvider.GOOGLE, new ProviderUserId("g-2"),
                new DisplayName("nick"), new Email("u@jamoai.app"), NOW);
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));
        when(profileRepository.findById(targetUserId)).thenReturn(Optional.empty());
        when(diaryCountPort.getCount(eq(targetUserId.value()), eq(false))).thenReturn(null);

        PublicProfileResult result = service.retrieve(new RetrieveProfileQuery(targetUserId));

        assertThat(result.diaryCount()).isNull();
    }

    @Test
    void retrieve_throws_when_user_not_found() {
        UserId targetUserId = UserId.generate();
        when(userRepository.findById(targetUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retrieve(new RetrieveProfileQuery(targetUserId)))
                .isInstanceOf(UserNotFoundException.class);
    }
}
