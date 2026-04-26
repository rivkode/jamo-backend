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

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
