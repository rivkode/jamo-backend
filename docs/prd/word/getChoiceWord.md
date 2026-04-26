---
api_id: word.getChoiceWord
http_method: GET
path: /api/v1/words/choice
auth: Y
controller: WordApiController.kt
handler: getChoiceWord
status: mined
---

# GET /api/v1/words/choice — 선택용 단어 후보

## 1. 요청 (Request)
- Header: `@LoginUser`
- Query: `part: Part (enum)`, `lastWordId?: Long`

## 2. 응답 (Response)
- 성공: `200 OK` + `WordDto.RetrieveWordInfoListResponse`

## 3. 비즈니스 로직 (요약)
1. `wordFacade.retrieveChoice(part, userId, lastWordId)` → 다음 단어 후보 페이지.

## 4. 데이터 의존
- DB read: words (part 필터, 사용자 학습 이력 제외 가능)

## 5. 예외 케이스
- 잘못된 part → 400 (enum binding 실패)
- 인증 실패 → 401

## 6. 암묵적 로직 (Implicit)
- `lastWordId`로 키 페이징 — cursor 역할.
- 페이지 크기는 facade 내부 고정 추정.

## 7. 호출자 (Clients)
- 모바일 (단어 선택 화면)

## 8. TODO / Open Questions
- [ ] 페이지 크기 명시화
- [ ] 이미 학습한 단어 제외 정책

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
