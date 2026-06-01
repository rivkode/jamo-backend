package app.backend.jamo.diary.domain.model.audio;

import java.util.Objects;
import java.util.UUID;

/**
 * AudioClip Aggregate 식별자 (UUID 래핑 VO).
 *
 * <p>저장 파일명은 {@code {id}.{ext}} 로 파생 — 추측 불가 capability URL 의 근거 (서빙은 무인증 + 이름이 곧 권한).
 * diary core / comment / sentence-feedback 의 UUID 일관 (ADR-0005).
 */
public record AudioClipId(UUID value) {

    public AudioClipId {
        Objects.requireNonNull(value, "value");
    }

    public static AudioClipId newId() {
        return new AudioClipId(UUID.randomUUID());
    }

    public static AudioClipId of(UUID value) {
        return new AudioClipId(value);
    }

    public String asString() {
        return value.toString();
    }
}
