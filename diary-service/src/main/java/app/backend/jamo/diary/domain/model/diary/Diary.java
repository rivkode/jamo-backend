package app.backend.jamo.diary.domain.model.diary;

import app.backend.jamo.diary.domain.exception.DiaryAccessDeniedException;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Diary Aggregate Root — 일기 1건 의 전 라이프사이클.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md (1-13 항목).
 *
 * <p><b>외래 ID (ADR-0005)</b>: {@code authorId} 는 다른 BC (identity-service) 의 Aggregate ID 이므로 raw
 * {@link UUID} 보유 + JPA 연관관계 / FK 미사용 (sentence-feedback 정합).
 *
 * <p><b>좋아요 카운터 동기화 (필수 invariant)</b>: {@link #likeCount} 는 denormalized 카운터 — {@code DiaryLike}
 * Aggregate INSERT/DELETE 와 같은 트랜잭션에서 {@link #onLikeAdded} / {@link #onLikeRemoved} 호출 의무.
 * 본 메서드는 {@code DiaryLike} 의 생성/삭제와 분리해 호출 시 카운터 drift (실제 row 수 ≠ likeCount) 발생 —
 * Application Service 가 두 작업을 단일 메서드로 묶어 invariant 위반 방지. 별도 Aggregate 분리 (사용자 결정) 로
 * 인한 트레이드오프: 동일 트랜잭션 안에서 두 Aggregate 모두 load + save 필요. 박제 §8.
 *
 * <p><b>댓글 카운터</b>: {@link #commentCount} 는 본 sub-domain (diary core) 에선 항상 0. comment
 * sub-domain 평가 시점에 {@code CommentCreated} / {@code CommentDeleted} cascade 로 갱신 — 본 PR 의
 * Aggregate 는 reconstitute 시 외부 값 받기만.
 *
 * <p><b>가드 메서드</b>:
 * <ul>
 *   <li>{@link #isAccessibleBy(UUID)} — 비공개 + 비작성자 차단 (404 통일 매핑)</li>
 *   <li>{@link #isOwnedBy(UUID)} — 작성자 only 오퍼레이션 (삭제) 가드</li>
 * </ul>
 */
public final class Diary {

    private final DiaryId id;
    private final UUID authorId;
    private final DiaryContent content;
    private final ImageUrls images;
    private final Tags tags;
    private final Visibility visibility;
    private int likeCount;
    private int commentCount;
    private final Instant createdAt;

    private Diary(
        DiaryId id,
        UUID authorId,
        DiaryContent content,
        ImageUrls images,
        Tags tags,
        Visibility visibility,
        int likeCount,
        int commentCount,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.authorId = Objects.requireNonNull(authorId, "authorId");
        this.content = Objects.requireNonNull(content, "content");
        this.images = Objects.requireNonNull(images, "images");
        this.tags = Objects.requireNonNull(tags, "tags");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        if (likeCount < 0 || commentCount < 0) {
            throw new IllegalArgumentException("counters must be non-negative");
        }
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    // ============================================================
    // Factories
    // ============================================================

    /**
     * 신규 작성 — 작성 직후 카운터 0.
     *
     * <p>박제 §3: visibility 미지정 시 default {@link Visibility#PUBLIC} 은 Application/Presentation 책임
     * (도메인은 명시 값 강제 — null 차단).
     */
    public static Diary create(
        DiaryId id,
        UUID authorId,
        DiaryContent content,
        ImageUrls images,
        Tags tags,
        Visibility visibility,
        Clock clock
    ) {
        Objects.requireNonNull(clock, "clock");
        return new Diary(id, authorId, content, images, tags, visibility, 0, 0, clock.instant());
    }

    /** Repository 복원용 — JpaEntity → Domain. invariant 검증 스킵 (DB 데이터는 이미 검증된 것으로 간주). */
    public static Diary reconstitute(
        DiaryId id,
        UUID authorId,
        DiaryContent content,
        ImageUrls images,
        Tags tags,
        Visibility visibility,
        int likeCount,
        int commentCount,
        Instant createdAt
    ) {
        return new Diary(id, authorId, content, images, tags, visibility, likeCount, commentCount, createdAt);
    }

    // ============================================================
    // Behavior
    // ============================================================

    /**
     * {@code DiaryLike} 신규 INSERT 직후 호출 — 같은 트랜잭션 안에서만 의미 있음. 메서드명이 "외부 cause"
     * (좋아요가 추가됨) 를 나타내 임의 호출 시 코드 리뷰에서 즉시 잡힘 (도메인 언어).
     *
     * <p>호출자 (Application Service) 책임:
     * <ul>
     *   <li>{@code DiaryLikeRepository.existsByDiaryIdAndUserId} 가 false 임을 사전 확인 (멱등성)</li>
     *   <li>{@code DiaryLike.create} + {@code save} 와 동일 트랜잭션</li>
     * </ul>
     */
    public void onLikeAdded() {
        this.likeCount++;
    }

    /**
     * {@code DiaryLike} DELETE 직후 호출 — 같은 트랜잭션 안에서만 의미 있음. 카운터 0 미만은 invariant 위반.
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
     * 비공개 + 비작성자 가드 — {@code get}, {@code toggleLike}, 피드 외 조회 시 사용. false 반환 시 호출자가
     * {@link DiaryNotFoundException} 발생 (404 통일).
     */
    public boolean isAccessibleBy(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        if (this.visibility == Visibility.PUBLIC) {
            return true;
        }
        return this.authorId.equals(userId);
    }

    /**
     * 작성자 only 가드 — 삭제 등에 사용. false 반환 시 호출자가 {@link DiaryAccessDeniedException} 발생
     * (Presentation 매핑은 404).
     */
    public boolean isOwnedBy(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return this.authorId.equals(userId);
    }

    // ============================================================
    // Getters
    // ============================================================

    public DiaryId id() {
        return id;
    }

    public UUID authorId() {
        return authorId;
    }

    public DiaryContent content() {
        return content;
    }

    public ImageUrls images() {
        return images;
    }

    public Tags tags() {
        return tags;
    }

    public Visibility visibility() {
        return visibility;
    }

    public int likeCount() {
        return likeCount;
    }

    public int commentCount() {
        return commentCount;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Diary that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
