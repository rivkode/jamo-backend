---
api_id: sentence.getMySentence
http_method: GET
path: /api/v1/sentences/me/{sentenceId}
auth: Y
controller: SentenceApiController.kt
handler: getMySentence
status: mined
---

# GET /api/v1/sentences/me/{sentenceId} — 내 문장 단건

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `sentenceId: Long`

## 2. 응답 (Response)
- 성공: `200 OK` + `SentenceDto.RetrieveResponse`

## 3. 비즈니스 로직 (요약)
1. `sentenceFacade.retrieveMySentence(userId, sentenceId)` → 본인 문장 검증 + 반환.

## 4. 데이터 의존
- DB read: sentences

## 5. 예외 케이스
- 본인 아님 → 403/404

## 6. 암묵적 로직 (Implicit)
- 다른 사용자의 sentenceId 조회 시 404 vs 403 정책.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 권한 거부 응답 코드 일관성

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
