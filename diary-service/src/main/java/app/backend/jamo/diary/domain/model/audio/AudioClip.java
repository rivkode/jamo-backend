package app.backend.jamo.diary.domain.model.audio;

import app.backend.jamo.diary.domain.exception.InvalidAudioException;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 업로드된 음성 녹음 한 건의 메타데이터 Aggregate Root (바이너리 자체는 {@link AudioStorage} 가 보관).
 *
 * <p>박제: API_SPEC.md 부록 E (음성 녹음→저장→재생 MVP). diarychat 음성 메시지 / TTS 결과물의 저장 토대.
 *
 * <p>불변식:
 * <ul>
 *   <li>본문(content) 비어있지 않음 + {@link #MAX_SIZE_BYTES} 이하</li>
 *   <li>content-type 이 {@link #ALLOWED_TYPES} 화이트리스트에 존재 (확장자 파생)</li>
 * </ul>
 *
 * <p>저장 파일명 {@code storedName = {id}.{ext}} 은 추측 불가 UUID 기반 — 서빙이 무인증이어도 이름 자체가
 * capability 역할 (security: capability URL).
 */
public class AudioClip {

    /** 단일 녹음 최대 크기 — 음성 메시지/TTS 산출물 기준 넉넉한 상한. 운영 모니터링 후 조정. */
    public static final long MAX_SIZE_BYTES = 25L * 1024 * 1024;

    /** 허용 content-type → 저장 확장자. 화이트리스트 외 거부 (임의 바이너리 업로드 차단). */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
        "audio/wav", "wav",
        "audio/x-wav", "wav",
        "audio/wave", "wav",
        "audio/mpeg", "mp3",
        "audio/mp4", "m4a",
        "audio/aac", "aac",
        "audio/webm", "webm",
        "audio/ogg", "ogg"
    );

    private final AudioClipId id;
    private final UUID ownerUserId;
    private final String storedName;
    private final String contentType;
    private final long sizeBytes;
    private final Instant createdAt;

    private AudioClip(AudioClipId id, UUID ownerUserId, String storedName,
                      String contentType, long sizeBytes, Instant createdAt) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.storedName = storedName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
    }

    /**
     * 신규 업로드로부터 메타데이터 생성 — 불변식 검증 + 저장 파일명 파생.
     *
     * @param ownerUserId 업로더 (인증 사용자)
     * @param contentType MIME type ({@link #ALLOWED_TYPES} 중 하나)
     * @param sizeBytes   본문 바이트 수 (1..{@link #MAX_SIZE_BYTES})
     * @param now         생성 시각
     */
    public static AudioClip create(UUID ownerUserId, String contentType, long sizeBytes, Instant now) {
        Objects.requireNonNull(ownerUserId, "ownerUserId");
        Objects.requireNonNull(now, "now");
        if (sizeBytes <= 0) {
            throw new InvalidAudioException("audio content is empty");
        }
        if (sizeBytes > MAX_SIZE_BYTES) {
            throw new InvalidAudioException("audio exceeds max size " + MAX_SIZE_BYTES + " bytes");
        }
        String normalizedType = normalize(contentType);
        String ext = ALLOWED_TYPES.get(normalizedType);
        if (ext == null) {
            throw new InvalidAudioException("unsupported audio content-type: " + contentType);
        }
        AudioClipId id = AudioClipId.newId();
        String storedName = id.asString() + "." + ext;
        return new AudioClip(id, ownerUserId, storedName, normalizedType, sizeBytes, now);
    }

    /** persistence 재구성용 (Mapper 전용). */
    public static AudioClip reconstitute(AudioClipId id, UUID ownerUserId, String storedName,
                                         String contentType, long sizeBytes, Instant createdAt) {
        return new AudioClip(id, ownerUserId, storedName, contentType, sizeBytes, createdAt);
    }

    private static String normalize(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new InvalidAudioException("audio content-type is required");
        }
        int semicolon = contentType.indexOf(';');
        String base = (semicolon >= 0 ? contentType.substring(0, semicolon) : contentType);
        return base.trim().toLowerCase();
    }

    public AudioClipId id() {
        return id;
    }

    public UUID ownerUserId() {
        return ownerUserId;
    }

    public String storedName() {
        return storedName;
    }

    public String contentType() {
        return contentType;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
