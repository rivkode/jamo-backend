-- 음성 녹음 메타데이터 (API_SPEC 부록 E — 녹음→저장→재생 MVP). 바이너리는 파일시스템(LocalAudioStorage).
--
-- ADR-0005: JPA 연관관계 / FK 제약 없음 — owner_user_id 는 외래 UUID 컬럼 + 인덱스만.
-- stored_name 은 {uuid}.{ext} 로 UNIQUE — 서빙 GET /audio/{name} 의 조회 키 + capability.

CREATE TABLE audio_clips (
    id            BINARY(16)   NOT NULL,
    owner_user_id BINARY(16)   NOT NULL,
    stored_name   VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_audio_clips_stored_name (stored_name),
    INDEX idx_audio_clips_owner (owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
