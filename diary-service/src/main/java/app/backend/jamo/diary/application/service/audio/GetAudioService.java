package app.backend.jamo.diary.application.service.audio;

import app.backend.jamo.diary.application.dto.audio.AudioContent;
import app.backend.jamo.diary.domain.exception.AudioClipNotFoundException;
import app.backend.jamo.diary.domain.model.audio.AudioClip;
import app.backend.jamo.diary.domain.model.audio.AudioStorage;
import app.backend.jamo.diary.domain.repository.AudioClipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장된 음성 재생 Use Case — 메타 검증(content-type 획득) 후 바이너리 반환.
 *
 * <p>서빙은 무인증(capability URL: 추측 불가 UUID 파일명) — 메타가 없으면 404. 파일명은 presentation 이
 * 형식 검증(path traversal 차단) 후 전달.
 */
@Service
@RequiredArgsConstructor
public class GetAudioService {

    private final AudioClipRepository repository;
    private final AudioStorage storage;

    @Transactional(readOnly = true)
    public AudioContent get(String storedName) {
        AudioClip clip = repository.findByStoredName(storedName)
            .orElseThrow(() -> new AudioClipNotFoundException("audio not found: " + storedName));
        byte[] content = storage.load(clip.storedName())
            .orElseThrow(() -> new AudioClipNotFoundException("audio binary missing: " + storedName));
        return new AudioContent(content, clip.contentType());
    }
}
