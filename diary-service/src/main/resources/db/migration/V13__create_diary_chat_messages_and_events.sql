-- diarychat S2-b — 메시지 + 롱폴 이벤트 로그 (API_SPEC 부록 E.2, diarychat-domain-policy-v2-apispec-e.md §8-b).
--
-- messageId/event id 는 BIGINT AUTO_INCREMENT (롱폴 before/after 숫자 커서, ID 순서=시간 순서).
-- ADR-0005: FK 없음 — 외래 UUID/room_id + INDEX 만. text 4000 = 1000 code points × 4 bytes(utf8mb4).

CREATE TABLE diary_chat_messages (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    room_id        BIGINT       NOT NULL,
    author_user_id BINARY(16)   NULL,            -- AI/SYSTEM 은 NULL (S4)
    text           VARCHAR(4000) NOT NULL,
    audio_url      VARCHAR(2048) NULL,
    source         VARCHAR(16)  NOT NULL,         -- USER / AI / SYSTEM
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- listMessages(before desc) / poll(after asc) 커버링: (room_id, id)
    INDEX idx_chat_messages_room_id (room_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE diary_chat_room_events (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    room_id       BIGINT      NOT NULL,
    type          VARCHAR(32) NOT NULL,           -- PARTICIPANT_JOINED / PARTICIPANT_LEFT / AI_TOGGLE_CHANGED
    actor_user_id BINARY(16)  NOT NULL,
    enabled       BOOLEAN     NULL,               -- AI_TOGGLE_CHANGED 만
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_chat_room_events_room_id (room_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
