---
api_id: word.retrieveReviewWords
http_method: GET
path: /api/v1/words/review/{wordListId}
auth: Y
controller: WordApiController.kt
handler: retrieveReviewWords
status: mined
---

# GET /api/v1/words/review/{wordListId} — 단어 복습 출제 세트

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `wordListId: Int`
- Query: `count: Int` (필수, 출제 단어 개수)

## 2. 응답 (Response)
- 성공: `200 OK` + `WordDto.RetrieveReviewWordResponse`

## 3. 비즈니스 로직 (요약)
1. `wordFacade.retrieveReviewWords(userId, wordListId, count)` → 복습 단어 N개 추출(랜덤/SRS 추정).

## 4. 데이터 의존
- DB read: user_words (wordListId 그룹)

## 5. 예외 케이스
- count 0 또는 음수 → 정책에 따라 400 (validation 어노테이션 미적용)
- wordListId 없음 → 404

## 6. 암묵적 로직 (Implicit)
- **count 검증 어노테이션 누락** — 매우 큰 값 전달 시 응답 비대화/성능 저하.
- 추출 알고리즘(랜덤 vs SRS)은 facade 의존.

## 7. 호출자 (Clients)
- 모바일 (복습 시작)

## 8. TODO / Open Questions
- [ ] count 상한
- [ ] 추출 알고리즘 명세

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: count 상한 추가 → `@FIX`
