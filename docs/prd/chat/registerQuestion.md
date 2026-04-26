---
api_id: chat.registerQuestion
http_method: POST
path: /api/v1/questions
auth: Y
controller: QuestionApiController.kt
handler: registerQuestion
status: mined
---

# POST /api/v1/questions — 질문 등록

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: `QuestionDto.RegisterRequest` (`@Valid`)

## 2. 응답 (Response)
- 성공: `201 Created` + `QuestionDto.RegisterResponse(questionInfo)`

## 3. 비즈니스 로직 (요약)
1. `questionFacade.registerQuestion(command, userId)` → 질문 저장.

## 4. 데이터 의존
- DB write: questions

## 5. 예외 케이스
- validation 실패 → 400

## 6. 암묵적 로직 (Implicit)
- (현재 정보로 판단 가능한 것 없음)

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 질문 후 자동 답변 생성 트리거 여부

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
