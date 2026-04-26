---
api_id: sentence.listMySentences
http_method: GET
path: /api/v1/sentences/me
auth: Y
controller: SentenceApiController.kt
handler: listMySentences
status: mined
---

# GET /api/v1/sentences/me — 내 문장 목록

## 1. 요청 (Request)
- Header: `@LoginUser`

## 2. 응답 (Response)
- 성공: `200 OK` + `SentenceDto.RetrieveListResponse`

## 3. 비즈니스 로직 (요약)
1. `sentenceFacade.retrieveMySentenceList(userId)` → 사용자 문장 전체.

## 4. 데이터 의존
- DB read: sentences (user_id)

## 5. 예외 케이스
- 인증 실패 → 401

## 6. 암묵적 로직 (Implicit)
- 페이징 없음.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 페이징 도입 필요 여부

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: 페이징 → `@FIX`
