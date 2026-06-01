package app.backend.jamo.diary.domain.repository;

import app.backend.jamo.diary.domain.model.audio.AudioClip;

import java.util.Optional;

/**
 * AudioClip 메타데이터 영속 port.
 */
public interface AudioClipRepository {

    AudioClip save(AudioClip clip);

    /** 서빙 시 저장 파일명으로 메타 조회 (존재 검증 + content-type 획득). */
    Optional<AudioClip> findByStoredName(String storedName);
}
