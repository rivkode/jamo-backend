-- diarychat S2-a — 채팅방 + 참여자 (API_SPEC 부록 E.2, decisions/diary/diarychat-domain-policy-v2-apispec-e.md).
--
-- roomId 는 BIGINT AUTO_INCREMENT (롱폴 숫자 커서 정합, v2 §1). ADR-0005: FK 없음 — 외래 UUID + INDEX 만.
-- 일기당 1방: diary_id UNIQUE. 참여자 멱등: (room_id, user_id) UNIQUE. isHost 미저장(host_user_id 파생, v2 §3).

CREATE TABLE diary_chat_rooms (
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    diary_id             BINARY(16)  NOT NULL,
    host_user_id         BINARY(16)  NOT NULL,
    ai_assistant_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at           DATETIME(6) NOT NULL,
    deleted_at           DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_diary_chat_rooms_diary (diary_id),
    INDEX idx_diary_chat_rooms_host (host_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE diary_chat_participants (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    room_id   BIGINT      NOT NULL,
    user_id   BINARY(16)  NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_chat_participants_room_user (room_id, user_id),
    INDEX idx_chat_participants_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
