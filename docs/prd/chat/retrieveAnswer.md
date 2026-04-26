---
api_id: chat.retrieveAnswer
http_method: GET
path: /api/v1/answers/{questionId}
auth: Y
controller: AnswerApiController.kt
handler: retrieveAnswer
status: mined
---

# GET /api/v1/answers/{questionId} — 질문에 대한 답변 조회

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `questionId: Long`

## 2. 응답 (Response)
- 성공: `200 OK` + `AnswerDto.RetrieveResponse(answerInfo)`

## 3. 비즈니스 로직 (요약)
1. `answerFacade.retrieveAnswer(userId, questionId)` → 답변 조회.

## 4. 데이터 의존
- DB read: answers, questions

## 5. 예외 케이스
- 인증 실패 → 401
- 답변 없음 → 404
- 권한 없음 → 403/404

## 6. 암묵적 로직 (Implicit)
- "answers/" 경로지만 questionId로 조회 — 답변은 question 1:1 추정.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 답변이 N개일 가능성
- [ ] 다른 사용자의 답변 조회 권한

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
