---
api_id: comment.delete
http_method: DELETE
path: /api/v1/comments/{commentId}
auth: Y
controller: CommentController.kt
handler: delete
status: mined
---

# DELETE /api/v1/comments/{commentId} — 댓글 삭제

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `commentId: Long`

## 2. 응답 (Response)
- 성공: `204 No Content`
- 실패: 401 (인증), 403 (작성자 아님), 404 (없음)

## 3. 비즈니스 로직 (요약)
1. `commentFacade.delete(commentId, userId)` → 작성자 검증 후 삭제(soft/hard 미확인).

## 4. 데이터 의존
- DB write: comments (delete or soft-delete)

## 5. 예외 케이스
- 작성자 아님 → 403/404
- 이미 삭제됨 → 404 또는 idempotent 204

## 6. 암묵적 로직 (Implicit)
- soft-delete vs hard-delete 정책 확인 필요.
- 자식(답글)이 있는 경우 처리 방침.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] soft-delete 여부
- [ ] 답글 처리(cascade vs orphan)

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
