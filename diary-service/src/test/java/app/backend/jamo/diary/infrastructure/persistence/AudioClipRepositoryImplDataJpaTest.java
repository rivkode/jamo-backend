package app.backend.jamo.diary.infrastructure.persistence;

import app.backend.jamo.diary.domain.model.audio.AudioClip;
import app.backend.jamo.diary.domain.repository.AudioClipRepository;
import app.backend.jamo.diary.infrastructure.persistence.repository.AudioClipRepositoryImpl;
import app.backend.jamo.diary.infrastructure.support.AbstractMySQLContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AudioClipRepositoryImpl 의 save / findByStoredName 정합 검증. Testcontainer MySQL 8.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AudioClipRepositoryImpl.class)
@ActiveProfiles("test")
class AudioClipRepositoryImplDataJpaTest extends AbstractMySQLContainerTest {

    @Autowired private AudioClipRepository repository;

    @Test
    void save_then_findByStoredName_roundtrip() {
        AudioClip clip = AudioClip.create(UUID.randomUUID(), "audio/wav", 1234,
            Instant.parse("2026-06-01T10:00:00Z"));
        repository.save(clip);

        var found = repository.findByStoredName(clip.storedName());

        assertThat(found).isPresent();
        assertThat(found.get().id().value()).isEqualTo(clip.id().value());
        assertThat(found.get().ownerUserId()).isEqualTo(clip.ownerUserId());
        assertThat(found.get().contentType()).isEqualTo("audio/wav");
        assertThat(found.get().sizeBytes()).isEqualTo(1234);
    }

    @Test
    void findByStoredName_absent_returns_empty() {
        assertThat(repository.findByStoredName("nope.wav")).isEmpty();
    }
}
