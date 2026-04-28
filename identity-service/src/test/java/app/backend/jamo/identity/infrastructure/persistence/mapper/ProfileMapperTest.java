package app.backend.jamo.identity.infrastructure.persistence.mapper;

import app.backend.jamo.identity.domain.model.profile.AvatarUrl;
import app.backend.jamo.identity.domain.model.profile.Bio;
import app.backend.jamo.identity.domain.model.profile.Locale;
import app.backend.jamo.identity.domain.model.profile.Profile;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.infrastructure.persistence.entity.ProfileJpaEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileMapperTest {

    private static final Instant T0 = Instant.parse("2026-04-28T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-04-28T10:05:00Z");

    @Test
    void toJpaEntity_maps_all_fields_with_optional_unwrapping() {
        UserId id = UserId.generate();
        Profile profile = Profile.restore(id,
                new Bio("hello"),
                new AvatarUrl("https://e.io/a.png"),
                new Locale("en"), T0, T1);

        ProfileJpaEntity entity = ProfileMapper.toJpaEntity(profile);

        assertThat(entity.getUserId()).isEqualTo(id.value());
        assertThat(entity.getBio()).isEqualTo("hello");
        assertThat(entity.getAvatarUrl()).isEqualTo("https://e.io/a.png");
        assertThat(entity.getLocale()).isEqualTo("en");
        assertThat(entity.getCreatedAt()).isEqualTo(T0);
        assertThat(entity.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void toJpaEntity_handles_null_optional_fields() {
        UserId id = UserId.generate();
        Profile profile = Profile.create(id, T0);  // bio/avatarUrl null, locale=DEFAULT

        ProfileJpaEntity entity = ProfileMapper.toJpaEntity(profile);

        assertThat(entity.getBio()).isNull();
        assertThat(entity.getAvatarUrl()).isNull();
        assertThat(entity.getLocale()).isEqualTo("ko");
    }

    @Test
    void toDomain_maps_all_fields_back() {
        UserId id = UserId.generate();
        ProfileJpaEntity entity = new ProfileJpaEntity(
                id.value(), "hi", "https://cdn.io/a.png", "en", T0, T1);

        Profile profile = ProfileMapper.toDomain(entity);

        assertThat(profile.id()).isEqualTo(id);
        assertThat(profile.bio()).contains(new Bio("hi"));
        assertThat(profile.avatarUrl()).contains(new AvatarUrl("https://cdn.io/a.png"));
        assertThat(profile.locale()).isEqualTo(new Locale("en"));
        assertThat(profile.createdAt()).isEqualTo(T0);
        assertThat(profile.updatedAt()).isEqualTo(T1);
    }

    @Test
    void toDomain_handles_null_optional_fields() {
        UserId id = UserId.generate();
        ProfileJpaEntity entity = new ProfileJpaEntity(
                id.value(), null, null, "ko", T0, T0);

        Profile profile = ProfileMapper.toDomain(entity);

        assertThat(profile.bio()).isEmpty();
        assertThat(profile.avatarUrl()).isEmpty();
        assertThat(profile.locale()).isEqualTo(Locale.DEFAULT);
    }

    @Test
    void mergeInto_overwrites_managed_entity_fields() {
        UserId id = UserId.generate();
        ProfileJpaEntity existing = new ProfileJpaEntity(
                id.value(), "old", null, "ko", T0, T0);
        Profile updated = Profile.restore(id,
                new Bio("new"),
                new AvatarUrl("https://e.io/a.png"),
                new Locale("en"), T0, T1);

        ProfileJpaEntity merged = ProfileMapper.mergeInto(existing, updated);

        assertThat(merged).isSameAs(existing);  // 같은 managed instance 반환
        assertThat(merged.getBio()).isEqualTo("new");
        assertThat(merged.getAvatarUrl()).isEqualTo("https://e.io/a.png");
        assertThat(merged.getLocale()).isEqualTo("en");
        assertThat(merged.getUpdatedAt()).isEqualTo(T1);
        assertThat(merged.getCreatedAt()).isEqualTo(T0);  // 변경 X
    }
}
