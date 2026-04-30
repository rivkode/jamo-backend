package app.backend.jamo.diary.domain.repository.cursor;

import app.backend.jamo.diary.domain.model.comment.CommentId;

import java.time.Instant;
import java.util.Objects;

/**
 * 댓글 목록의 chronological keyset 페이징 cursor.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §6 (chronological 단일 정렬, cursor opaque).
 *
 * <p>{@code (created_at, comment_id)} 튜플로 SQL 인덱스 (`(diary_id, created_at asc, comment_id asc)`) 와
 * 1:1 대응. Aggregate 의 일관성 경계나 비즈니스 invariant 가 아닌 <b>Repository port 의 페이지네이션 메커니즘</b> —
 * 따라서 {@code domain/model} 이 아닌 {@code domain/repository/cursor} 에 위치 (port 의 일부, diary core
 * {@code RecentFeedCursor} 정합).
 *
 * <p>첫 페이지는 cursor null 로 호출자가 표현. 인코딩 (base64) 은 Application 책임 — 도메인은 의미만 보유.
 *
 * <p>diary core 의 RECENT 정렬 (created_at <b>desc</b>, diary_id desc) 와 달리 댓글은 chronological
 * <b>asc</b> 정렬 (대화 순서) — 같은 record 구조이지만 정렬 방향은 Repository 구현체 시그니처에 명시.
 */
public record CommentCursor(Instant lastCreatedAt, CommentId lastCommentId) {

    public CommentCursor {
        Objects.requireNonNull(lastCreatedAt, "lastCreatedAt");
        Objects.requireNonNull(lastCommentId, "lastCommentId");
    }
}
