---
api_id: chat.registerAnswer
http_method: POST
path: /api/v1/answers
auth: Y
controller: AnswerApiController.kt
handler: registerAnswer
status: mined
---

# POST /api/v1/answers — 답변 생성 (AI)

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: `AnswerDto.RegisterRequest` (⚠️ `@Valid` 누락)

## 2. 응답 (Response)
- 성공: `201 Created` + `AnswerDto.RegisterResponse(answerInfo)`

## 3. 비즈니스 로직 (요약)
1. `answerFacade.generateAnswer(userId, registerAnswer)` → AI로 답변 생성·저장.

## 4. 데이터 의존
- DB write: answers
- 외부 API: AI 모델

## 5. 예외 케이스
- 외부 모델 실패 → 5xx

## 6. 암묵적 로직 (Implicit)
- 핸들러명은 `registerAnswer`인데 Facade 메서드는 `generateAnswer` — 사용자가 답변 입력이 아니라 **AI 생성**임을 시사.
- `@Valid` 미적용.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 사용자 직접 입력 vs AI 생성 구분
- [ ] questionId가 Body에 포함되는가

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: `@Valid` 추가 → `@FIX`
