---
api_id: word.createChoiceWord
http_method: POST
path: /api/v1/words/choice
auth: Y
controller: WordApiController.kt
handler: createChoiceWord
status: mined
---

# POST /api/v1/words/choice — 선택한 단어 등록(학습 시작)

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: `WordDto.RegisterChoiceRequest` (`@Valid`)

## 2. 응답 (Response)
- 성공: `201 Created` + `WordDto.RetrieveWordResponse`

## 3. 비즈니스 로직 (요약)
1. `request.toCommand(userId=userId)` → `wordFacade.registerChoiceWord(command)` → 단어를 사용자 학습 목록에 추가.

## 4. 데이터 의존
- DB write: user_words / word_selections

## 5. 예외 케이스
- 중복 등록 → 정책에 따라 409 또는 idempotent

## 6. 암묵적 로직 (Implicit)
- 같은 단어를 중복 선택하면 어떻게 되는지 확인 필요.
- 응답이 단건(`RetrieveWordResponse`) — 등록된 단어 1개의 상세.

## 7. 호출자 (Clients)
- 모바일

## 8. TODO / Open Questions
- [ ] 중복 등록 정책
- [ ] 일괄 등록 지원 여부

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
