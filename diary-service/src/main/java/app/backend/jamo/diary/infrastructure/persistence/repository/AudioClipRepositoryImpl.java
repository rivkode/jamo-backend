package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.domain.model.audio.AudioClip;
import app.backend.jamo.diary.domain.repository.AudioClipRepository;
import app.backend.jamo.diary.infrastructure.persistence.mapper.AudioClipMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * AudioClipRepository port 구현.
 */
@Repository
@RequiredArgsConstructor
public class AudioClipRepositoryImpl implements AudioClipRepository {

    private final SpringDataAudioClipRepository jpa;

    @Override
    public AudioClip save(AudioClip clip) {
        return AudioClipMapper.toDomain(jpa.save(AudioClipMapper.toJpaEntity(clip)));
    }

    @Override
    public Optional<AudioClip> findByStoredName(String storedName) {
        return jpa.findByStoredName(storedName).map(AudioClipMapper::toDomain);
    }
}
