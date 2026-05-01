-- D-a-2-impl-infra: comment Infrastructure layer 마이그레이션.
-- ADR-0005 정합: JPA 연관관계 / DB FK constraint 없이 외래 ID 컬럼과 인덱스만 보유.

-- =========================================================
-- 1. comments
-- =========================================================
-- chronological list: (diary_id, created_at ASC, id ASC)
-- parent_id: depth 1단 답글 cascade 용도. FK 없이 인덱스만 사용.
CREATE TABLE comments (
    id              BINARY(16) NOT NULL,
    diary_id        BINARY(16) NOT NULL,
    author_id       BINARY(16) NOT NULL,
    content         VARCHAR(2000) NOT NULL,
    parent_id       BINARY(16) NULL,
    like_count      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_comments_diary_created_at (diary_id, created_at ASC, id ASC),
    INDEX idx_comments_parent_id (parent_id),
    INDEX idx_comments_author_id (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================================================
-- 2. comment_likes
-- =========================================================
-- (comment_id, user_id) UNIQUE: ToggleCommentLikeService 의 동시 INSERT race 최후 보루.
-- user/comment composite index: list likedByMe 일괄 조회.
CREATE TABLE comment_likes (
    id          BINARY(16) NOT NULL,
    comment_id  BINARY(16) NOT NULL,
    user_id     BINARY(16) NOT NULL,
    created_at  TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_comment_like_comment_user (comment_id, user_id),
    INDEX idx_comment_likes_user_comment (user_id, comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
