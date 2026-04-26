---
api_id: comment.create
http_method: POST
path: /api/v1/diaries/{diaryId}/comments
auth: Y
controller: DiaryCommentController.kt
handler: create
status: mined
---

# POST /api/v1/diaries/{diaryId}/comments — 일기 댓글 작성

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `diaryId: Long`
- Body: `CommentDto.CreateRequest` (`@Valid`)

## 2. 응답 (Response)
- 성공: `201 Created` + `CommentDto.CommentResponse`

## 3. 비즈니스 로직 (요약)
1. `request.toCommand(diaryId, userId)` → `commentFacade.create(...)` → 저장.

## 4. 데이터 의존
- DB write: comments

## 5. 예외 케이스
- validation → 400
- 일기 없음 → 404

## 6. 암묵적 로직 (Implicit)
- 답글(parentId) 지원 여부는 Request DTO 확인 필요.
- 알림(notification) 발송 트리거 가능성 (이벤트 발행 추정).

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 답글 깊이 제한
- [ ] 알림 발송 정책

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
