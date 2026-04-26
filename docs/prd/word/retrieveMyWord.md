---
api_id: word.retrieveMyWord
http_method: GET
path: /api/v1/words/me
auth: Y
controller: WordApiController.kt
handler: retrieveMyWord
status: mined
---

# GET /api/v1/words/me — 내 단어 목록

## 1. 요청 (Request)
- Header: `@LoginUser`

## 2. 응답 (Response)
- 성공: `200 OK` + `WordDto.RetrieveWordInfoListResponse`

## 3. 비즈니스 로직 (요약)
1. `wordFacade.retrieveMyWord(userId)` → 사용자 학습 중인 단어 전체.

## 4. 데이터 의존
- DB read: words / user_words 관계

## 5. 예외 케이스
- 인증 실패 → 401

## 6. 암묵적 로직 (Implicit)
- 페이징 없음.

## 7. 호출자 (Clients)
- 모바일

## 8. TODO / Open Questions
- [ ] 페이징/필터
- [ ] 정렬 기준

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
