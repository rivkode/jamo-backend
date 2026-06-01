package app.backend.jamo.diary.domain.model.diarychat;

import app.backend.jamo.diary.domain.exception.ChatRoomForbiddenException;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 일기 1건에 연결된 채팅방 Aggregate Root.
 *
 * <p>박제: decisions/diary/diarychat-domain-policy-v2-apispec-e.md. 일기당 1방(unique diary_id), host =
 * 일기 작성자(불변). 메시지/참여자는 별 Aggregate(roomId 외래참조) — Giant Aggregate 회피.
 *
 * <p><b>late identity</b>: {@link #create} 로 만든 신규 room 은 영속 전까지 {@code id == null}.
 * {@code repository.save} 가 생성 키를 채운 인스턴스를 반환. transient 인스턴스는 컬렉션/식별에 쓰지 않는다.
 *
 * <p>불변식:
 * <ul>
 *   <li>aiAssistant 토글은 <b>host 만</b> ({@link #setAiAssistant} — 비호스트 {@link ChatRoomForbiddenException} 403)</li>
 *   <li>삭제는 {@link #markDeleted} (DiaryDeleted Saga 전용, 후속) — soft-delete</li>
 * </ul>
 */
public class DiaryChatRoom {

    private final RoomId id;            // null = 영속 전 (late identity)
    private final UUID diaryId;
    private final UUID hostUserId;
    private boolean aiAssistantEnabled;
    private final Instant createdAt;
    private Instant deletedAt;          // null = active

    private DiaryChatRoom(RoomId id, UUID diaryId, UUID hostUserId, boolean aiAssistantEnabled,
                          Instant createdAt, Instant deletedAt) {
        this.id = id;
        this.diaryId = diaryId;
        this.hostUserId = hostUserId;
        this.aiAssistantEnabled = aiAssistantEnabled;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
    }

    /** 신규 방 — id 미정(영속 시 확정). host = 일기 작성자. */
    public static DiaryChatRoom create(UUID diaryId, UUID hostUserId, boolean aiAssistantEnabled, Clock clock) {
        Objects.requireNonNull(diaryId, "diaryId");
        Objects.requireNonNull(hostUserId, "hostUserId");
        Objects.requireNonNull(clock, "clock");
        return new DiaryChatRoom(null, diaryId, hostUserId, aiAssistantEnabled, clock.instant(), null);
    }

    /** persistence 재구성 (Mapper 전용). */
    public static DiaryChatRoom reconstitute(RoomId id, UUID diaryId, UUID hostUserId,
                                             boolean aiAssistantEnabled, Instant createdAt, Instant deletedAt) {
        Objects.requireNonNull(id, "id");
        return new DiaryChatRoom(id, diaryId, hostUserId, aiAssistantEnabled, createdAt, deletedAt);
    }

    /** aiAssistant 토글 — host 만. 멱등(동일 값 재설정 안전). */
    public void setAiAssistant(UUID actorUserId, boolean enabled) {
        if (!isHost(actorUserId)) {
            throw new ChatRoomForbiddenException("only host can toggle ai assistant");
        }
        this.aiAssistantEnabled = enabled;
    }

    /** DiaryDeleted Saga cascade 전용 (후속) — soft-delete 멱등. */
    public void markDeleted(Clock clock) {
        if (this.deletedAt == null) {
            this.deletedAt = clock.instant();
        }
    }

    public boolean isHost(UUID userId) {
        return hostUserId.equals(userId);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public RoomId id() {
        return id;
    }

    public UUID diaryId() {
        return diaryId;
    }

    public UUID hostUserId() {
        return hostUserId;
    }

    public boolean aiAssistantEnabled() {
        return aiAssistantEnabled;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant deletedAt() {
        return deletedAt;
    }
}
