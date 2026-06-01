package app.backend.jamo.diary.domain.model.diarychat;

/**
 * ChatMessage 식별자 — int64 (BIGINT auto-increment) 래핑 VO. 롱폴 before/after 숫자 커서 정합.
 * ID 순서 = INSERT 순서 = 시간 순서 (단일 MySQL auto-increment). late identity (영속 후 확정).
 */
public record MessageId(long value) {

    public MessageId {
        if (value <= 0) {
            throw new IllegalArgumentException("MessageId must be positive: " + value);
        }
    }

    public static MessageId of(long value) {
        return new MessageId(value);
    }

    public static MessageId fromString(String value) {
        try {
            return new MessageId(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid MessageId: " + value, e);
        }
    }
}
