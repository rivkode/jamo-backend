---
api_id: chat.retrieveMyQuestionList
http_method: GET
path: /api/v1/questions/me
auth: Y
controller: QuestionApiController.kt
handler: retrieveMyQuestionList
status: mined
---

# GET /api/v1/questions/me — 내 질문 목록

## 1. 요청 (Request)
- Header: `@LoginUser`

## 2. 응답 (Response)
- 성공: `200 OK` + `QuestionDto.RetrieveListResponse(questionInfoList)`

## 3. 비즈니스 로직 (요약)
1. `questionFacade.retrieveMyQuestionList(userId)` → 사용자의 질문 전체.

## 4. 데이터 의존
- DB read: questions (user_id 필터)

## 5. 예외 케이스
- 인증 실패 → 401

## 6. 암묵적 로직 (Implicit)
- 페이징 없음. 컨트롤러에 주석 처리된 `/me/{questionId}` endpoint 존재 — 단건 조회는 미사용/미구현.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 페이징 필요 여부
- [ ] 단건 조회(`/me/{questionId}`) 부활 여부

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
