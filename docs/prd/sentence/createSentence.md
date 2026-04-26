---
api_id: sentence.registerSentence
http_method: POST
path: /api/v1/sentences
auth: Y
controller: SentenceApiController.kt
handler: registerSentence
status: mined
---

# POST /api/v1/sentences — 문장 등록

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: `SentenceDto.RegisterRequest` (`@Valid`)

## 2. 응답 (Response)
- 성공: `201 Created` + `SentenceDto.RegisterResponse(sentenceInfo)`

## 3. 비즈니스 로직 (요약)
1. `sentenceFacade.registerSentence(command, userId)` → 저장.

## 4. 데이터 의존
- DB write: sentences

## 5. 예외 케이스
- validation → 400

## 6. 암묵적 로직 (Implicit)
- 문장 등록 후 자동 피드백 트리거 여부 — 별도 endpoint(`/{sentenceId}/feedback`) 호출이 필요한지 확인.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 자동 첨삭 트리거 여부

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
