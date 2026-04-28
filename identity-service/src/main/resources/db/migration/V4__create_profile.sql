-- profile 도메인 (PRD profile/*, decisions/identity/profile-prd-evaluation.md, profile-app-infra-decisions.md).
-- shared identifier 패턴 (IDDD Ch.10): user_id 가 PK 겸 외래 ID 의 같은 값.
-- FK constraint 미사용 (ADR-0005 정합) — 인덱스는 PK 자동 인덱스로 충분.
-- display_name 컬럼 미생성 — User SoT (Phase 6-a 박제).
--
-- locale 길이 8: ISO 639-1 (2자) + 향후 country suffix 여유 (e.g., "zh-Hant").
-- 본 슬라이스는 ko/en 화이트리스트 (Locale VO).

CREATE TABLE profiles (
    user_id BINARY(16) NOT NULL,
    bio VARCHAR(200) NULL,
    avatar_url VARCHAR(500) NULL,
    locale VARCHAR(8) NOT NULL DEFAULT 'ko',
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- version: optimistic locking (UserJpaEntity 와 정합). 동시 PATCH 시 lost-update 검출.
