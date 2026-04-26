---
api_id: comment.list
http_method: GET
path: /api/v1/diaries/{diaryId}/comments
auth: Y
controller: DiaryCommentController.kt
handler: list
status: mined
---

# GET /api/v1/diaries/{diaryId}/comments — 일기 댓글 목록 (cursor 페이징)

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `diaryId: Long`
- Query: `cursor?: String`, `size?: Int (default 20)`

## 2. 응답 (Response)
- 성공: `200 OK` + `CommentDto.CommentListResponse`

## 3. 비즈니스 로직 (요약)
1. `commentFacade.list(diaryId, cursor, size, userId)` → cursor 페이지 응답.

## 4. 데이터 의존
- DB read: comments (diary_id 필터, cursor 기반)

## 5. 예외 케이스
- 인증 실패 → 401
- 비공개 일기 접근 → 403/404 (Facade 내부 가드)

## 6. 암묵적 로직 (Implicit)
- size 디폴트 20, max 미확인 — 클라이언트가 큰 값 전달 시 가드 필요.
- cursor 형식(opaque string) — Facade 책임.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] size 상한
- [ ] 정렬 기준(작성일 내림차순 등)

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
