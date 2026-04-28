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

## 9. KEEP/DROP/FIX 분류

**판정: FIX** (KEEP + 변경 사항 박제)

### KEEP 사유
- 댓글 삭제는 사용자 자기 통제권 핵심 기능 (자기 댓글 회수).

### FIX 항목

상세 박제: [`docs/decisions/diary/comment-domain-policy.md`](../../decisions/diary/comment-domain-policy.md).

1. **ID 타입 UUID 전환** — `commentId: Long` → `UUID` (path).
2. **hard-delete 채택** — soft-delete 미채택. 이유: (a) 사용자 의도 명확 (회수), (b) GDPR / 개인정보 친화, (c) 운영 단순화 (소프트 플래그 필터링 부담 X), (d) 답글 cascade 처리 단순화.
3. **답글 cascade-delete** — 부모 댓글 삭제 시 자식 답글도 hard-delete. orphan 회피. (depth 1단 제한이라 cascade 단순.)
4. **권한: 작성자 only** — 일기 작성자 강제 삭제 권한 미부여. 신고 시스템 (Non-Goals) 으로 처리. PRD §6 의 "soft/hard 미확인" 부채 해소.
5. **404 통일 (IDOR 보호)** — 작성자 아님 / 댓글 없음 모두 404. 403 (Forbidden) 미사용 — 댓글 존재 노출 회피.
6. **비멱등 (이미 삭제 → 404)** — idempotent 204 미채택. 이유: hard-delete 채택으로 "이미 삭제" = "존재하지 않음" — 404 가 명확. PRD §6 의 "soft-delete vs hard-delete" 부채 해소.
7. **응답 본문 없음 (204 No Content)** — 명시.
8. **CommentDeleted 이벤트** — Kafka 이벤트 신설 미채택 (현재). 일기 삭제 시 댓글 cascade 는 `DiaryDeleted` (PR D-a-3 박제 예정) 의 구독자 핸들러에서 처리. 개별 댓글 삭제 이벤트는 platform 랭킹에 영향 없음 (CommentCreated 차감 미정책 — 후속).

### 영향 범위 (구현 PR 에서)
- diary-service: `DeleteCommentService` + `Comment.delete()` 도메인 메서드 + cascade (parentId 자식 일괄 삭제) + `CommentExceptionHandler` (Forbidden / NotFound 모두 404).
- contracts: 변경 없음.
