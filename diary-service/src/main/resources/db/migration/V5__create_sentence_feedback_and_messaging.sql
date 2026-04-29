-- D-a-5-impl-infra: sentence-feedback Infrastructure layer 첫 마이그레이션.
-- 3 테이블 통합 (한 슬라이스 = 한 마이그레이션 정합):
--   1) sentence_feedback   — Aggregate 영속 (decisions/diary/sentence-feedback-domain-policy.md)
--   2) outbox_event        — Outbox 패턴 (CLAUDE.md NEVER: Outbox 없이 도메인 이벤트 발행 금지)
--   3) processed_event     — Kafka Consumer 멱등성 (CLAUDE.md NEVER: 멱등성 미처리 금지)
--
-- ADR-0005 정합: 외래 ID (user_id / diary_id) BINARY(16) primitive 보유, JPA 연관관계 / FK constraint
-- 미사용. 인덱스만 명시.

-- =========================================================
-- 1. sentence_feedback
-- =========================================================
-- §1 UUID 일관 (BINARY(16)).
-- §2 라이프사이클 6 status (REQUESTED/SUGGESTED/ACCEPTED/REJECTED/EXPIRED/FAILED).
-- §5 diary_id NULL 허용 (작성 전 미리보기).
-- §10 tone NULL 허용 (사용자 미명시 — chat-service default).
-- §13 diary_id 인덱스 (DiaryDeleted Saga cascade).
-- §14 user_id 인덱스 (UserWithdrawalRequested Saga cascade + 사용자별 quota).
-- §3 expires_at 인덱스 (배치 EXPIRED 전이 — D-a-5-impl-batch).
-- suggestions: JSON 컬럼 (Hibernate 6.4+ @JdbcTypeCode(SqlTypes.JSON)).
--   - PRD §4 데이터 모델 정합 — Suggestion VO 는 Aggregate 내부 (외부 검색 X) → 정규화 불요.
--   - 정규화 시 별 테이블 + JOIN 비용 / 검색 요구 부재 → JSON 채택.
-- text 컬럼 길이: SentenceText 1..50 code points → utf8mb4 1 cp = max 4 bytes → 200 bytes 충분 (여유).
--   - VARCHAR(255) 채택 (MySQL utf8mb4 with row_format=DYNAMIC).
-- failure_reason / rejection_reason: 1000 cp 상한 (FAILURE_REASON_MAX_CODE_POINTS / REJECTION_REASON_MAX_CODE_POINTS).
--   - utf8mb4 1cp max 4byte → 4000 byte. TEXT 채택 (VARCHAR 너무 큼, BLOB 아님).
CREATE TABLE sentence_feedback (
    id                       BINARY(16) NOT NULL,
    user_id                  BINARY(16) NOT NULL,
    diary_id                 BINARY(16) NULL,
    original_sentence        VARCHAR(255) NOT NULL,
    tone                     VARCHAR(16) NULL,
    status                   VARCHAR(16) NOT NULL,
    suggestions              JSON NOT NULL,
    decision_suggestion_id   BINARY(16) NULL,
    rejection_reason         TEXT NULL,
    failure_reason           TEXT NULL,
    expires_at               TIMESTAMP(3) NULL,
    decided_at               TIMESTAMP(3) NULL,
    created_at               TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_sentence_feedback_user_status (user_id, status),
    INDEX idx_sentence_feedback_diary_id (diary_id),
    INDEX idx_sentence_feedback_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================================================
-- 2. outbox_event
-- =========================================================
-- Outbox 패턴: domain transaction 안에서 row insert → 별 poller 가 Kafka 로 발행 → published_at 채움.
-- event_id UNIQUE: 멱등 publish (중복 insert 방지). poller 가 같은 row 두 번 발행해도 consumer 측
-- ProcessedEvent 가 다시 차단.
-- aggregate_type / aggregate_id: 운영 추적 / 부분 재발행 도구용. payload 는 Jackson 으로 직렬화한 record.
CREATE TABLE outbox_event (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    event_id        VARCHAR(36) NOT NULL,
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(64) NOT NULL,
    type            VARCHAR(128) NOT NULL,
    topic           VARCHAR(64) NOT NULL,
    payload         JSON NOT NULL,
    created_at      TIMESTAMP(3) NOT NULL,
    published_at    TIMESTAMP(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_event_id (event_id),
    INDEX idx_outbox_event_published_at (published_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================================================
-- 3. processed_event
-- =========================================================
-- Kafka Consumer 멱등성 — CLAUDE.md "Kafka Consumer 멱등성 미처리 금지" 의무.
-- 같은 (consumer_id, event_id) 가 이미 있으면 처리 skip. consumer_id = listener bean 식별자
-- (예: "DiaryDeletedListener" / "UserWithdrawalRequestedListener").
-- - 같은 이벤트라도 여러 listener 가 다른 의미로 처리할 수 있어 (consumer_id, event_id) 복합 UNIQUE.
CREATE TABLE processed_event (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    consumer_id   VARCHAR(128) NOT NULL,
    event_id      VARCHAR(36) NOT NULL,
    processed_at  TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_processed_event_consumer_event (consumer_id, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
