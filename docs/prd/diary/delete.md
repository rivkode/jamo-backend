---
api_id: diary.delete
http_method: DELETE
path: /api/v1/diaries/{diaryId}
auth: Y
controller: DiaryController.kt
handler: delete
status: mined
---

# DELETE /api/v1/diaries/{diaryId} — 일기 삭제

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `diaryId: Long`

## 2. 응답 (Response)
- 성공: `204 No Content`
- 실패: 401, 403/404

## 3. 비즈니스 로직 (요약)
1. `diaryFacade.delete(diaryId, userId)` → 작성자 검증 후 삭제.

## 4. 데이터 의존
- DB write: diaries (cascade: comments, likes)

## 5. 예외 케이스
- 작성자 아님 → 403/404
- 이미 삭제됨 → 404 또는 idempotent

## 6. 암묵적 로직 (Implicit)
- soft-delete vs hard-delete 미확인.
- 연관 데이터(댓글, 좋아요, 채팅방) cascade 정책.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] cascade 정책 명시
- [ ] soft-delete 여부

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
