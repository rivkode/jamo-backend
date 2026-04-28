package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.MyProfileResult;
import app.backend.jamo.identity.application.dto.UpdateMyProfileCommand;
import app.backend.jamo.identity.domain.event.DisplayNameChanged;
import app.backend.jamo.identity.domain.exception.DisplayNameChangeTooFrequentException;
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
import app.backend.jamo.identity.domain.repository.DisplayNameChangeRateLimiter;
import app.backend.jamo.identity.domain.repository.ProfileRepository;
import app.backend.jamo.identity.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateMyProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-28T10:00:00Z");

    private UserRepository userRepository;
    private ProfileRepository profileRepository;
    private DisplayNameChangeRateLimiter rateLimiter;
    private ApplicationEventPublisher eventPublisher;
    private UpdateMyProfileService service;

    private User user;
    private UserId userId;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        profileRepository = mock(ProfileRepository.class);
        rateLimiter = mock(DisplayNameChangeRateLimiter.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new UpdateMyProfileService(userRepository, profileRepository,
                rateLimiter, eventPublisher, clock);

        user = User.registerWithOAuth(OAuthProvider.GOOGLE, new ProviderUserId("g-1"),
                new DisplayName("old"), new Email("u@jamoai.app"), NOW);
        userId = user.id();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void update_displayName_renames_user_publishes_event_and_saves() {
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());

        MyProfileResult result = service.update(
                new UpdateMyProfileCommand(userId, "newName", null, null, null));

        assertThat(user.displayName()).isEqualTo(new DisplayName("newName"));
        assertThat(result.displayName()).isEqualTo(new DisplayName("newName"));
        verify(rateLimiter).check(userId);
        verify(eventPublisher).publishEvent(argThat((Object e) ->
                e instanceof DisplayNameChanged dnc
                        && dnc.userId().equals(userId)
                        && dnc.ttl().equals(Duration.ofDays(7))));
        verify(userRepository).save(user);
    }

    @Test
    void update_skips_displayName_when_null() {
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());

        service.update(new UpdateMyProfileCommand(userId, null, "new bio", null, null));

        assertThat(user.displayName()).isEqualTo(new DisplayName("old"));
        verify(rateLimiter, never()).check(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(userRepository, never()).save(any());  // displayName 미변경 → save 호출 X (code review M3)
    }

    @Test
    void update_throws_when_rate_limit_hit_no_changes_persisted() {
        doThrow(new DisplayNameChangeTooFrequentException("rate")).when(rateLimiter).check(userId);

        assertThatThrownBy(() -> service.update(
                new UpdateMyProfileCommand(userId, "newName", null, null, null)))
                .isInstanceOf(DisplayNameChangeTooFrequentException.class);

        // user.rename 미호출, save 미호출, 이벤트 발행 X
        assertThat(user.displayName()).isEqualTo(new DisplayName("old"));
        verify(userRepository, never()).save(any());
        verify(profileRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void update_lazy_creates_profile_when_missing() {
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());

        MyProfileResult result = service.update(
                new UpdateMyProfileCommand(userId, null, "new bio", null, "en"));

        verify(profileRepository).save(argThat((Profile p) ->
                p.id().equals(userId)
                        && p.bio().map(Bio::value).orElse("").equals("new bio")
                        && p.locale().equals(new Locale("en"))
                        && p.createdAt().equals(NOW)   // lazy create 시점 = 첫 PATCH 시점 (test review M2)
                        && p.updatedAt().equals(NOW)));
        assertThat(result.bio()).isEqualTo(new Bio("new bio"));
        assertThat(result.locale()).isEqualTo(new Locale("en"));
    }

    @Test
    void update_unsets_bio_when_empty_string() {
        Profile existing = Profile.restore(userId, new Bio("old bio"), null, Locale.DEFAULT, NOW, NOW);
        when(profileRepository.findById(userId)).thenReturn(Optional.of(existing));

        MyProfileResult result = service.update(
                new UpdateMyProfileCommand(userId, null, "   ", null, null));

        assertThat(result.bio()).isNull();
    }

    @Test
    void update_unsets_avatarUrl_when_empty_string() {
        Profile existing = Profile.restore(userId, null,
                new AvatarUrl("https://e.io/a.png"), Locale.DEFAULT, NOW, NOW);
        when(profileRepository.findById(userId)).thenReturn(Optional.of(existing));

        MyProfileResult result = service.update(
                new UpdateMyProfileCommand(userId, null, null, "  ", null));

        assertThat(result.avatarUrl()).isNull();
    }

    @Test
    void update_locale_blank_throws_explicitly() {
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());

        // applyLocale 명시 거부 (code review M2) — VO 화이트리스트 메시지보다 명확
        assertThatThrownBy(() -> service.update(
                new UpdateMyProfileCommand(userId, null, null, null, "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("locale must not be blank");
    }

    @Test
    void update_locale_unsupported_code_throws_via_VO() {
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                new UpdateMyProfileCommand(userId, null, null, null, "fr")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported locale");
    }

    @Test
    void update_throws_when_user_not_found() {
        UserId other = UserId.generate();
        when(userRepository.findById(other)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                new UpdateMyProfileCommand(other, null, "x", null, null)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void update_full_change_applies_all_fields() {
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());

        MyProfileResult result = service.update(
                new UpdateMyProfileCommand(userId, "newName", "new bio",
                        "https://e.io/a.png", "en"));

        assertThat(result.displayName()).isEqualTo(new DisplayName("newName"));
        assertThat(result.bio()).isEqualTo(new Bio("new bio"));
        assertThat(result.avatarUrl()).isEqualTo(new AvatarUrl("https://e.io/a.png"));
        assertThat(result.locale()).isEqualTo(new Locale("en"));
        verify(rateLimiter, times(1)).check(userId);
        verify(eventPublisher, times(1)).publishEvent(any(DisplayNameChanged.class));
    }

    @Test
    void update_avatar_url_with_userInfo_throws_via_VO() {
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                new UpdateMyProfileCommand(userId, null, null,
                        "https://user:pass@e.io/a.png", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userInfo");
    }
}
