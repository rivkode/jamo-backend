package app.backend.jamo.diary.domain.model.commentlike;

import app.backend.jamo.diary.domain.model.comment.CommentId;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * CommentLike Aggregate Root — 사용자가 댓글에 누른 좋아요 1건.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §8 (별도 Aggregate, DiaryLike 패턴 정합).
 *
 * <p><b>유니크 제약</b>: {@code (commentId, userId)} 동시 INSERT 차단. {@link CommentLikeId} 는 PK 이지만
 * 비즈니스 식별은 외래 ID 조합 — Repository 는 {@code findByCommentIdAndUserId} / {@code deleteByCommentIdAndUserId}
 * 로 멱등 UPSERT/DELETE 지원.
 *
 * <p><b>외래 ID (ADR-0005)</b>: {@code commentId} 는 {@link CommentId} VO 보유 (같은 BC), {@code userId} 는
 * 다른 BC (identity-service) Aggregate ID 라 raw {@link UUID}. {@code Comment} Aggregate 의
 * {@code likeCount} 카운터 동기화는 같은 트랜잭션 안에서 호출자 (Application Service) 책임 — 분리 호출 시
 * drift (실제 {@code comment_likes} row 수 ≠ {@code Comment.likeCount}) 발생.
 *
 * <p><b>비공개 일기 가드 + 자기 댓글 좋아요 허용</b>: 박제 §8 — 비공개 일기 댓글은 Application Service 가
 * {@code Diary.isAccessibleBy} 로 사전 차단, 자기 댓글 좋아요는 별도 검증 없음. 본 Aggregate 자체는 가드 미수행 (단일 책임).
 *
 * <p>본 Aggregate 는 상태 전이가 없는 단순 fact (CRUD) — UPSERT (create) / DELETE (toggle off) 만 존재.
 */
public final class CommentLike {

    private final CommentLikeId id;
    private final CommentId commentId;
    private final UUID userId;
    private final Instant createdAt;

    private CommentLike(CommentLikeId id, CommentId commentId, UUID userId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.commentId = Objects.requireNonNull(commentId, "commentId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    // ============================================================
    // Factories
    // ============================================================

    /** 신규 좋아요 — Aggregate ID 신규 발급. */
    public static CommentLike create(CommentId commentId, UUID userId, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return new CommentLike(CommentLikeId.newId(), commentId, userId, clock.instant());
    }

    /** Repository 복원용. */
    public static CommentLike reconstitute(CommentLikeId id, CommentId commentId, UUID userId, Instant createdAt) {
        return new CommentLike(id, commentId, userId, createdAt);
    }

    // ============================================================
    // Getters
    // ============================================================

    public CommentLikeId id() {
        return id;
    }

    public CommentId commentId() {
        return commentId;
    }

    public UUID userId() {
        return userId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommentLike that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
