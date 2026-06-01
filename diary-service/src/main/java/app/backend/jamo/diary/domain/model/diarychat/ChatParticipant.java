package app.backend.jamo.diary.domain.model.diarychat;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 채팅방 참여자 — (roomId, userId) 자연키로 식별 (surrogate PK 는 infra 한정).
 *
 * <p>박제: decisions/diary/diarychat-domain-policy-v2-apispec-e.md §3 — isHost 는 저장하지 않고
 * {@code room.hostUserId == userId} 로 파생(host leave→rejoin 엣지 자동 해소). 본 Aggregate 는 가입 사실만.
 *
 * <p>join 멱등 — 같은 (roomId,userId) 는 unique 제약으로 1행. count 파생 participantCount 정확성 보장.
 */
public final class ChatParticipant {

    private final RoomId roomId;
    private final UUID userId;
    private final Instant joinedAt;

    private ChatParticipant(RoomId roomId, UUID userId, Instant joinedAt) {
        this.roomId = roomId;
        this.userId = userId;
        this.joinedAt = joinedAt;
    }

    public static ChatParticipant join(RoomId roomId, UUID userId, Clock clock) {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(clock, "clock");
        return new ChatParticipant(roomId, userId, clock.instant());
    }

    public static ChatParticipant reconstitute(RoomId roomId, UUID userId, Instant joinedAt) {
        return new ChatParticipant(roomId, userId, joinedAt);
    }

    public RoomId roomId() {
        return roomId;
    }

    public UUID userId() {
        return userId;
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatParticipant that)) {
            return false;
        }
        return roomId.equals(that.roomId) && userId.equals(that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomId, userId);
    }
}
