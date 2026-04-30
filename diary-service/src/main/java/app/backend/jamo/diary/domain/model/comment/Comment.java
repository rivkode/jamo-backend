package app.backend.jamo.diary.domain.model.comment;

import app.backend.jamo.diary.domain.model.diary.DiaryId;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Comment Aggregate Root — 일기 댓글 1건의 라이프사이클.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md (1-10 항목).
 *
 * <p><b>외래 ID (ADR-0005)</b>: {@code diaryId} 는 {@link DiaryId} VO 보유 (같은 BC). {@code authorId} 는 다른
 * BC (identity-service) 의 Aggregate ID 이므로 raw {@link UUID} 보유 (diary core / sentence-feedback 정합).
 * {@code parentId} 는 같은 BC 안의 다른 Comment 식별자 — Aggregate 간 직접 참조 금지 (Vernon 12장) 이므로
 * {@link CommentId} 만 nullable 보유 (null = 루트 댓글).
 *
 * <p><b>좋아요 카운터 동기화 (필수 invariant)</b>: {@link #likeCount} 는 denormalized 카운터 — {@code CommentLike}
 * Aggregate INSERT/DELETE 와 같은 트랜잭션에서 {@link #onLikeAdded} / {@link #onLikeRemoved} 호출 의무 (diary core
 * 정합). 본 메서드는 {@code CommentLike} 의 생성/삭제와 분리해 호출 시 카운터 drift (실제 row 수 ≠ likeCount) 발생 —
 * Application Service 가 두 작업을 단일 메서드로 묶어 invariant 위반 방지. {@code (commentId, userId)} UNIQUE
 * 위반 catch fallback path 에서는 {@code onLikeAdded} 미호출 — race 시 다른 tx 가 이미 INSERT + 카운터 증가 처리.
 * fallback 트랜잭션은 read-only 재조회만 수행 ({@code ToggleDiaryLikeService} 의 동시성 race window + 멱등
 * fallback 패턴 정합).
 *
 * <p><b>답글 cascade 와 카운터의 관계</b>: 답글 hard-delete cascade 시 그 답글에 달려 있던 {@code comment_likes}
 * row 도 함께 사라진다. 부모 {@link Comment} 의 {@link #likeCount} 는 변동 없으며, 사라지는 자식 Comment 자체가 더 이상
 * "load + onLikeRemoved" 호출 경로에 없다 — cascade 삭제는 카운터 동기화가 아니라 row 자체 소멸이므로 invariant 무관.
 * (ddd-architect Q1 권고)
 *
 * <p><b>답글 깊이 1단 검증</b>: Aggregate 가 아닌 Application Service 책임 (사용자 결정). {@link #isReply()} /
 * {@link #isRoot()} 헬퍼만 제공 — Application Service 가 parent 조회 후 {@code if (parent.isReply()) throw
 * InvalidCommentParentException(...)} 패턴.
 *
 * <p><b>비공개 일기 가드</b>: Comment Aggregate 는 {@code Diary.visibility} 상태에 의존하지 않는다 (ddd-architect Q4
 * 정합). Application Service 가 {@code Diary.isAccessibleBy(userId)} 사전 호출.
 *
 * <p><b>equals/hashCode</b>: {@link CommentId} 기반 — mutable 필드 ({@link #likeCount}) 미포함 (Diary 정합).
 */
public final class Comment {

    private final CommentId id;
    private final DiaryId diaryId;
    private final UUID authorId;
    private final CommentContent content;
    private final CommentId parentId;
    private int likeCount;
    private final Instant createdAt;

    private Comment(
        CommentId id,
        DiaryId diaryId,
        UUID authorId,
        CommentContent content,
        CommentId parentId,
        int likeCount,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.diaryId = Objects.requireNonNull(diaryId, "diaryId");
        this.authorId = Objects.requireNonNull(authorId, "authorId");
        this.content = Objects.requireNonNull(content, "content");
        this.parentId = parentId; // nullable — 루트 댓글
        if (likeCount < 0) {
            throw new IllegalArgumentException("likeCount must be non-negative");
        }
        this.likeCount = likeCount;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    // ============================================================
    // Factories
    // ============================================================

    /**
     * 신규 작성 — 작성 직후 카운터 0.
     *
     * @param parentIdOrNull null = 루트 댓글, non-null = 1단 답글 (depth 검증은 Application 책임)
     */
    public static Comment create(
        CommentId id,
        DiaryId diaryId,
        UUID authorId,
        CommentContent content,
        CommentId parentIdOrNull,
        Clock clock
    ) {
        Objects.requireNonNull(clock, "clock");
        return new Comment(id, diaryId, authorId, content, parentIdOrNull, 0, clock.instant());
    }

    /** Repository 복원용 — JpaEntity → Domain. invariant 검증 스킵 (DB 데이터는 이미 검증된 것으로 간주). */
    public static Comment reconstitute(
        CommentId id,
        DiaryId diaryId,
        UUID authorId,
        CommentContent content,
        CommentId parentIdOrNull,
        int likeCount,
        Instant createdAt
    ) {
        return new Comment(id, diaryId, authorId, content, parentIdOrNull, likeCount, createdAt);
    }

    // ============================================================
    // Behavior
    // ============================================================

    /**
     * {@code CommentLike} 신규 INSERT 직후 호출 — 같은 트랜잭션 안에서만 의미 있음. 메서드명이 "외부 cause"
     * (좋아요가 추가됨) 를 나타내 임의 호출 시 코드 리뷰에서 즉시 잡힘 (도메인 언어, diary core 정합).
     *
     * <p>호출자 (Application Service) 책임:
     * <ul>
     *   <li>{@code CommentLikeRepository.existsByCommentIdAndUserId} 가 false 임을 사전 확인 (멱등성)</li>
     *   <li>{@code CommentLike.create} + {@code save} 와 동일 트랜잭션</li>
     *   <li>{@code (commentId, userId)} UNIQUE 위반 catch fallback path 에서는 호출 금지 — race 시 다른 tx 가
     *   이미 INSERT + 카운터 증가 처리. fallback 트랜잭션은 read-only 재조회만 수행
     *   ({@code ToggleDiaryLikeService} 의 동시성 race window + 멱등 fallback 패턴 정합)</li>
     * </ul>
     */
    public void onLikeAdded() {
        this.likeCount++;
    }

    /**
     * {@code CommentLike} DELETE 직후 호출 — 같은 트랜잭션 안에서만 의미 있음. 카운터 0 미만은 invariant 위반.
     *
     * <p>호출자 책임: DELETE 가 실제 1 row 영향을 미쳤음을 사전 확인 (멱등 DELETE 시 호출 금지).
     */
    public void onLikeRemoved() {
        if (this.likeCount <= 0) {
            throw new IllegalStateException("likeCount cannot go below zero");
        }
        this.likeCount--;
    }

    /**
     * 작성자 only 가드 — 삭제 등에 사용. false 반환 시 호출자가 {@link app.backend.jamo.diary.domain.exception.CommentAccessDeniedException}
     * 발생 (Presentation 매핑은 404, IDOR 보호).
     */
    public boolean isOwnedBy(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return this.authorId.equals(userId);
    }

    /** 답글 여부 — Application Service 의 depth 1단 검증에서 {@code if (parent.isReply()) throw ...}. */
    public boolean isReply() {
        return this.parentId != null;
    }

    /** 루트 댓글 여부. */
    public boolean isRoot() {
        return this.parentId == null;
    }

    // ============================================================
    // Getters
    // ============================================================

    public CommentId id() {
        return id;
    }

    public DiaryId diaryId() {
        return diaryId;
    }

    public UUID authorId() {
        return authorId;
    }

    public CommentContent content() {
        return content;
    }

    /** {@link Optional#empty()} = 루트 댓글, present = 1단 답글. */
    public Optional<CommentId> parentId() {
        return Optional.ofNullable(parentId);
    }

    public int likeCount() {
        return likeCount;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Comment that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
