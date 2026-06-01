package app.backend.jamo.diary.application.service.audio;

import app.backend.jamo.diary.application.dto.audio.AudioUploadResult;
import app.backend.jamo.diary.application.dto.audio.UploadAudioCommand;
import app.backend.jamo.diary.domain.model.audio.AudioClip;
import app.backend.jamo.diary.domain.model.audio.AudioSignature;
import app.backend.jamo.diary.domain.model.audio.AudioStorage;
import app.backend.jamo.diary.domain.repository.AudioClipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * 음성 업로드 Use Case — 도메인 불변식 검증 → magic byte 검사 → 바이너리 저장 → 메타데이터 영속.
 *
 * <p><b>트랜잭션 경계 없음</b> (code-reviewer H2): 최대 25MB 파일 write 를 DB 트랜잭션 안에 두면 커넥션을
 * 파일 크기에 비례해 점유해 풀 고갈 위험. 본 Use Case 의 DB 쓰기는 단건 {@code repository.save} 뿐이라
 * Spring Data 자체 트랜잭션으로 충분하다. 파일 I/O 는 트랜잭션 밖에서 수행.
 *
 * <p>저장 순서: 바이너리({@link AudioStorage}) 먼저 → 메타({@link AudioClipRepository}). 메타 save 실패 시
 * 고아 파일이 남을 수 있으나(추측 불가 이름이라 무해 + 서빙은 메타 선조회라 노출 안 됨), 반대 순서면 메타는
 * 있는데 파일이 없어 서빙 시 404 가 되는, 더 혼란스러운 상태가 된다. 고아 파일 정리 배치는 후속 (M1).
 */
@Service
@RequiredArgsConstructor
public class UploadAudioService {

    private final AudioClipRepository repository;
    private final AudioStorage storage;
    private final Clock clock;

    public AudioUploadResult upload(UploadAudioCommand command) {
        AudioSignature.rejectIfNotAudio(command.content());
        AudioClip clip = AudioClip.create(
            command.ownerUserId(),
            command.contentType(),
            command.content().length,
            Instant.now(clock)
        );
        storage.store(clip.storedName(), command.content());
        repository.save(clip);
        return new AudioUploadResult(clip.storedName(), clip.contentType(), clip.sizeBytes());
    }
}
