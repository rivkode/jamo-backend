package app.backend.jamo.identity.domain.model.user;

import java.util.Objects;

public record DisplayName(String value) {

    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 32;

    public DisplayName {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("displayName length out of range");
        }
    }

    /**
     * provider 가 알려준 raw nickname 을 받아 32자 도메인 한계로 자른다 (ADR-0006 결정 3).
     * blank/null 은 거부 — extractor 단계에서 이미 걸러짐을 가정.
     */
    public static DisplayNameTruncation truncated(String raw) {
        Objects.requireNonNull(raw, "raw");
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("displayName must not be blank after trim");
        }
        if (trimmed.length() <= MAX_LENGTH) {
            return new DisplayNameTruncation(new DisplayName(trimmed), false);
        }
        return new DisplayNameTruncation(new DisplayName(trimmed.substring(0, MAX_LENGTH)), true);
    }
}
