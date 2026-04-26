---
api_id: sentence.retrieveFeedback
http_method: GET
path: /api/v1/sentences/{sentenceId}/feedback
auth: Y
controller: SentenceApiController.kt
handler: retrieveFeedback
status: mined
---

# GET /api/v1/sentences/{sentenceId}/feedback — 문장 첨삭 피드백 조회

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `sentenceId: Long`

## 2. 응답 (Response)
- 성공: `200 OK` + `FeedbackDto.RetrieveResponse`

## 3. 비즈니스 로직 (요약)
1. `feedbackFacade.retrieveFeedback(userId, sentenceId)` → 첨삭 결과 반환.

## 4. 데이터 의존
- DB read: feedbacks (sentence_id)

## 5. 예외 케이스
- 피드백 없음 → 404
- 권한 없음 → 403

## 6. 암묵적 로직 (Implicit)
- FeedbackController에 주석 처리된 `GET /feedbacks/{sentenceId}`가 있었음 — 이 endpoint가 대체.
- 응답 DTO가 `FeedbackDto`로 cross-domain 의존 (sentence interface에서 feedback DTO 사용).

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] cross-domain DTO 의존 정리

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: DTO 위치 정리 → `@FIX`
