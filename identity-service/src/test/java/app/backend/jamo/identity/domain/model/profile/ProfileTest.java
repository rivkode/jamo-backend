package app.backend.jamo.identity.domain.model.profile;

import app.backend.jamo.identity.domain.model.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileTest {

    private static final Instant T0 = Instant.parse("2026-04-28T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-04-28T10:05:00Z");

    @Test
    void create_initializes_with_default_locale_and_null_optional_fields() {
        UserId id = UserId.generate();

        Profile profile = Profile.create(id, T0);

        assertThat(profile.id()).isEqualTo(id);
        assertThat(profile.bio()).isEmpty();
        assertThat(profile.avatarUrl()).isEmpty();
        assertThat(profile.locale()).isEqualTo(Locale.DEFAULT);
        assertThat(profile.createdAt()).isEqualTo(T0);
        assertThat(profile.updatedAt()).isEqualTo(T0);
    }

    @Test
    void restore_preserves_all_fields() {
        UserId id = UserId.generate();
        Bio bio = new Bio("hello");
        AvatarUrl avatar = new AvatarUrl("https://example.com/a.png");
        Locale locale = new Locale("en");

        Profile profile = Profile.restore(id, bio, avatar, locale, T0, T1);

        assertThat(profile.id()).isEqualTo(id);
        assertThat(profile.bio()).contains(bio);
        assertThat(profile.avatarUrl()).contains(avatar);
        assertThat(profile.locale()).isEqualTo(locale);
        assertThat(profile.createdAt()).isEqualTo(T0);
        assertThat(profile.updatedAt()).isEqualTo(T1);
    }

    @Test
    void changeBio_updates_field_and_updatedAt() {
        Profile profile = Profile.create(UserId.generate(), T0);
        Bio bio = new Bio("new bio");

        profile.changeBio(bio, T1);

        assertThat(profile.bio()).contains(bio);
        assertThat(profile.updatedAt()).isEqualTo(T1);
        assertThat(profile.createdAt()).isEqualTo(T0); // 변경 X
    }

    @Test
    void changeBio_rejects_null() {
        Profile profile = Profile.create(UserId.generate(), T0);

        assertThatThrownBy(() -> profile.changeBio(null, T1))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("bio");
    }

    @Test
    void unsetBio_clears_field() {
        Profile profile = Profile.restore(UserId.generate(), new Bio("old"), null, Locale.DEFAULT, T0, T0);

        profile.unsetBio(T1);

        assertThat(profile.bio()).isEmpty();
        assertThat(profile.updatedAt()).isEqualTo(T1);
    }

    @Test
    void changeAvatarUrl_updates_field_and_updatedAt() {
        Profile profile = Profile.create(UserId.generate(), T0);
        AvatarUrl avatar = new AvatarUrl("https://cdn.example.com/x.png");

        profile.changeAvatarUrl(avatar, T1);

        assertThat(profile.avatarUrl()).contains(avatar);
        assertThat(profile.updatedAt()).isEqualTo(T1);
    }

    @Test
    void changeAvatarUrl_rejects_null() {
        Profile profile = Profile.create(UserId.generate(), T0);

        assertThatThrownBy(() -> profile.changeAvatarUrl(null, T1))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("avatarUrl");
    }

    @Test
    void unsetAvatarUrl_clears_field() {
        Profile profile = Profile.restore(UserId.generate(), null,
                new AvatarUrl("https://e.io/a.png"), Locale.DEFAULT, T0, T0);

        profile.unsetAvatarUrl(T1);

        assertThat(profile.avatarUrl()).isEmpty();
        assertThat(profile.updatedAt()).isEqualTo(T1);
    }

    @Test
    void changeLocale_updates_field_and_updatedAt() {
        Profile profile = Profile.create(UserId.generate(), T0);
        Locale locale = new Locale("en");

        profile.changeLocale(locale, T1);

        assertThat(profile.locale()).isEqualTo(locale);
        assertThat(profile.updatedAt()).isEqualTo(T1);
    }

    @Test
    void changeLocale_rejects_null() {
        Profile profile = Profile.create(UserId.generate(), T0);

        assertThatThrownBy(() -> profile.changeLocale(null, T1))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("locale");
    }

    @Test
    void create_rejects_null_id() {
        assertThatThrownBy(() -> Profile.create(null, T0))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id");
    }

    @Test
    void create_rejects_null_now() {
        assertThatThrownBy(() -> Profile.create(UserId.generate(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shared_identifier_invariant_holds() {
        UserId userId = UserId.generate();

        Profile profile = Profile.create(userId, T0);

        // shared identifier 패턴: Profile.id 는 UserId 와 *같은 값* (참조 동일성은 invariant 아님 —
        // 추후 방어적 복사 도입 시에도 invariant 유지)
        assertThat(profile.id()).isEqualTo(userId);
    }
}
