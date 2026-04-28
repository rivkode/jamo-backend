---
api_id: comment.toggleLike
http_method: POST
path: /api/v1/comments/{commentId}/like
auth: Y
controller: CommentController.kt
handler: toggleLike
status: mined
---

# POST /api/v1/comments/{commentId}/like — 댓글 좋아요 토글

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `commentId: Long`
- Body: `CommentDto.LikeToggleRequest { liked: Boolean }` (`@Valid`)

## 2. 응답 (Response)
- 성공: `200 OK` + `CommentDto.LikeToggleResponse`

## 3. 비즈니스 로직 (요약)
1. `commentLikeFacade.setLiked(commentId, userId, request.liked)` → 좋아요 set/unset.

## 4. 데이터 의존
- DB write: comment_likes (upsert/delete)

## 5. 예외 케이스
- 인증 실패 → 401
- 댓글 없음 → 404

## 6. 암묵적 로직 (Implicit)
- "토글"이라는 핸들러명이지만 클라이언트가 명시적으로 `liked: true/false`를 보냄 — 멱등성 확보.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 좋아요 카운트 응답 포함 여부

## 9. KEEP/DROP/FIX 분류

**판정: FIX** (KEEP + 변경 사항 박제)

### KEEP 사유
- 댓글 좋아요는 소셜 인터랙션 기본 기능. **명시적 boolean 멱등 설계** 가 이미 좋음 (PRD §6 "토글이라는 핸들러명이지만 클라이언트가 명시적으로 `liked: true/false`") — 클라이언트 retry 시에도 안전. 본 패턴 유지.

### FIX 항목

상세 박제: [`docs/decisions/diary/comment-domain-policy.md`](../../decisions/diary/comment-domain-policy.md).

1. **ID 타입 UUID 전환** — `commentId: Long` → `UUID` (path).
2. **응답 likeCount 포함** — `LikeToggleResponse` 가 `commentId / liked / likeCount` 3 필드. PRD §8 의 "좋아요 카운트 응답 포함 여부" Open Question 해소.
3. **자기 댓글 좋아요 허용** — 사용자가 자기 댓글에도 좋아요 가능 (UX 자유도). 별도 검증 없음.
4. **비공개 일기 가드 — 404** — 비공개 일기의 댓글 좋아요 시도 시 404 (IDOR 보호). list 와 동일 정책.
5. **댓글 없음 → 404** — 멱등성 손상 없음 (좋아요 대상 자체 부재).
6. **likeCount 갱신** — `(commentId, userId)` 유니크 제약 + `liked=true` 시 INSERT IGNORE / `liked=false` 시 DELETE. 카운터는 `comments.like_count` 컬럼 (denormalized) 또는 read model. 구현은 코드 슬라이스 시점.
7. **자기 좋아요 알림 X** — `CommentLiked` 이벤트는 알림 도메인 도입 시 박제 (현재 Non-Goals). 본 endpoint 자체는 이벤트 발행 미수행 (랭킹 영향 없음).

### 영향 범위 (구현 PR 에서)
- diary-service: `CommentLike` Entity + `ToggleCommentLikeService` + idempotent UPSERT/DELETE + likeCount 동기화.
- contracts: 변경 없음.
