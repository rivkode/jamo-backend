---
api_id: diary.acceptSentenceFeedback
http_method: POST
path: /api/v1/diaries/sentence-feedback/{feedbackId}/accept
auth: Y
controller: DiarySentenceFeedbackController.java
handler: acceptFeedback
status: proposed
---

# POST /api/v1/diaries/sentence-feedback/{feedbackId}/accept — 문장 피드백 제안 수락

## 0. 배경
`POST /sentence-feedback`(`requestSentenceFeedback.md`)으로 받은 제안 중 하나를 사용자가 채택할 때 호출. `SentenceFeedback` Aggregate 를 SUGGESTED → ACCEPTED 상태로 전이하고 채택한 `suggestionId` 와 결정 시각을 기록. 라이프사이클 전체는 `requestSentenceFeedback.md` 의 0.1 섹션 참조.

## 1. 요청 (Request)
- Header: `@LoginUser` (인증 필수)
- Path: `feedbackId` (UUID)
- Body: `AcceptSentenceFeedbackRequest`
  - `suggestionId: String` (필수, request 응답의 `suggestions[].suggestionId` 중 하나)

## 2. 응답 (Response)
- 성공: `200 OK` + `SentenceFeedbackResponse` (status=ACCEPTED, `decisionSuggestionId` 포함, `decidedAt` 세팅)
- 실패: 400 (validation, suggestionId 형식 오류), 401 (인증), 403 (다른 사용자의 feedback), 404 (없음), 409 (`SENTENCE_FEEDBACK_INVALID_TRANSITION` — 이미 final 상태)

## 3. 비즈니스 로직 (요약)
1. `feedbackId` 로 Aggregate 로드, 본인 소유 검증 (소유자 != 호출자 → 403)
2. `SentenceFeedback.accept(suggestionId, clock)` — 상태 전이 + `decisionSuggestionId` / `decidedAt` 기록
3. persist
4. **`SentenceFeedbackAccepted` 이벤트 Outbox 발행** → platform-service 활동 점수 가산 (수락 시 추가 점수)

## 4. 데이터 의존
- DB read/write: `sentence_feedback` (diary 스키마)
- Kafka (Outbox): `SentenceFeedbackAccepted`

## 5. 예외 케이스
- 다른 사용자 소유 → 403
- 없는 `feedbackId` → 404
- 이미 final(ACCEPTED/REJECTED/EXPIRED/FAILED) → 409 (`SENTENCE_FEEDBACK_INVALID_TRANSITION`)
- 알 수 없는 `suggestionId` → 400 (`SENTENCE_FEEDBACK_UNKNOWN_SUGGESTION`)

## 6. 암묵적 로직 (Implicit)
- ACCEPTED 후 사용자가 일기 작성을 마치고 저장하면 일기 본문에는 채택한 문장이 들어감 — 일기 저장 흐름과는 별개 트랜잭션
- ACCEPTED 횟수가 사용자 활동 점수의 가중치에 영향 (정책은 platform-service)
- ACCEPTED는 학습 신호로도 활용 가능 (어떤 제안이 채택되는지)

## 7. 호출자 (Clients)
- 모바일/웹 (일기 작성 화면 — 제안 채택 버튼)

## 8. TODO / Open Questions
- [ ] ACCEPTED 시 일기 본문에 자동 반영 vs 클라이언트 책임
- [ ] ACCEPTED 점수 가중치 정책
- [ ] 다중 suggestion 동시 채택 가능성 (현재는 단일)

## 9. KEEP/DROP/FIX 분류
- 신규 PRD (proposed)
