package app.backend.jamo.diary.domain.model.diarychat;

/**
 * DiaryChatRoom 식별자 — int64 (BIGINT auto-increment) 래핑 VO.
 *
 * <p>박제: decisions/diary/diarychat-domain-policy-v2-apispec-e.md §1 — 롱폴 숫자 커서(before/after) 정합을
 * 위해 diarychat 한정 UUID 대신 int64. 단일 MySQL auto-increment 라 ID 순서 = INSERT 순서 = 시간 순서.
 *
 * <p>ID 는 영속 후 확정(late identity) — 신규 room 은 영속 전까지 RoomId 미보유.
 */
public record RoomId(long value) {

    public RoomId {
        if (value <= 0) {
            throw new IllegalArgumentException("RoomId must be positive: " + value);
        }
    }

    public static RoomId of(long value) {
        return new RoomId(value);
    }

    /** path variable 등 문자열 파싱 — 형식 오류 시 IAE (presentation 이 400 매핑). */
    public static RoomId fromString(String value) {
        try {
            return new RoomId(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid RoomId: " + value, e);
        }
    }
}
