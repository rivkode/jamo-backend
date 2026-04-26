---
api_id: diary.listMyFeed
http_method: GET
path: /api/v1/diaries/me
auth: Y
controller: DiaryController.kt
handler: listMyFeed
status: mined
---

# GET /api/v1/diaries/me — 내 일기 목록

## 1. 요청 (Request)
- Header: `@LoginUser`
- Query: `cursor?: String`, `size?: Int (default 10)`

## 2. 응답 (Response)
- 성공: `200 OK` + `DiaryDto.FeedResponse`

## 3. 비즈니스 로직 (요약)
1. `diaryFacade.getMyFeed(userId, cursor, size)` → 사용자 본인 일기 페이지.

## 4. 데이터 의존
- DB read: diaries (user_id 필터)

## 5. 예외 케이스
- 인증 실패 → 401

## 6. 암묵적 로직 (Implicit)
- public/private 모두 포함 추정 (본인 조회).

## 7. 호출자 (Clients)
- 모바일/웹 (마이페이지)

## 8. TODO / Open Questions
- [ ] 정렬 옵션 부재 — 항상 최신순 가정?

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
