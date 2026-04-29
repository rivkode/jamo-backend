-- D-a-3-impl-infra: diary core Infrastructure layer 마이그레이션.
-- 2 테이블 통합 (한 슬라이스 = 한 마이그레이션 정합 — V5 sentence-feedback 정합):
--   1) diaries     — Diary Aggregate 영속 (decisions/diary/diary-domain-policy.md)
--   2) diary_likes — DiaryLike Aggregate 영속 (별도 Aggregate, 사용자 결정)
--
-- ADR-0005 정합: 외래 ID (author_id / user_id) BINARY(16) primitive 보유, JPA 연관관계 / FK constraint
-- 미사용. 인덱스만 명시.

-- =========================================================
-- 1. diaries
-- =========================================================
-- §1 UUID 일관 (BINARY(16)).
-- §2 visibility VARCHAR(16) — PUBLIC / PRIVATE.
-- §3 content TEXT (DiaryContent 1..2000 code points × 4 bytes = 8000 bytes < 65535).
-- §3 tags / images JSON (Tags max 10 / ImageUrls max 5 — Aggregate 내부 컬렉션, 정규화 불요).
-- §7 keyset 페이징 인덱스 3종:
--   - public RECENT: (visibility, created_at DESC, id DESC)
--   - public POPULAR: (visibility, like_count DESC, created_at DESC, id DESC)
--   - my recent: (author_id, created_at DESC, id DESC)
-- §8 like_count INT (denormalized 카운터, 0..21억 — 산업 표준 좋아요 수 충분).
-- §9 author_id 인덱스 (UserWithdrawalRequested Saga cascade + my feed).
CREATE TABLE diaries (
    id              BINARY(16) NOT NULL,
    author_id       BINARY(16) NOT NULL,
    content         TEXT NOT NULL,
    images          JSON NOT NULL,
    tags            JSON NOT NULL,
    visibility      VARCHAR(16) NOT NULL,
    like_count      INT NOT NULL DEFAULT 0,
    comment_count   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_diaries_visibility_created_at (visibility, created_at DESC, id DESC),
    INDEX idx_diaries_visibility_like_count (visibility, like_count DESC, created_at DESC, id DESC),
    INDEX idx_diaries_author_created_at (author_id, created_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================================================
-- 2. diary_likes
-- =========================================================
-- §8 멱등 UPSERT/DELETE — `(diary_id, user_id)` UNIQUE 제약 필수 (drift 방지 최후 보루,
-- ToggleDiaryLikeService javadoc 의 동시성 race window 정합).
-- §9 diary_id 인덱스 (DiaryDeleted Saga cascade — DiaryLikeOnDiaryDeletedListener).
-- §11 user_id 인덱스 (UserWithdrawalRequested Saga cascade).
-- likedByMe 일괄 조회 (피드 N+1 회피) 는 (user_id, diary_id) 인덱스 활용 — 본 인덱스로 IN 절 효율.
CREATE TABLE diary_likes (
    id          BINARY(16) NOT NULL,
    diary_id    BINARY(16) NOT NULL,
    user_id     BINARY(16) NOT NULL,
    created_at  TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_diary_like_diary_user (diary_id, user_id),
    INDEX idx_diary_likes_user_diary (user_id, diary_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
