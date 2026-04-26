---
api_id: diary.rejectSentenceFeedback
http_method: POST
path: /api/v1/diaries/sentence-feedback/{feedbackId}/reject
auth: Y
controller: DiarySentenceFeedbackController.java
handler: rejectFeedback
status: proposed
---

# POST /api/v1/diaries/sentence-feedback/{feedbackId}/reject — 문장 피드백 제안 거부

## 0. 배경
`POST /sentence-feedback`(`requestSentenceFeedback.md`)으로 받은 제안을 사용자가 모두 거부할 때 호출. `SentenceFeedback` Aggregate 를 SUGGESTED → REJECTED 상태로 전이. 라이프사이클 전체는 `requestSentenceFeedback.md` 의 0.1 섹션 참조.

## 1. 요청 (Request)
- Header: `@LoginUser` (인증 필수)
- Path: `feedbackId` (UUID)
- Body: `RejectSentenceFeedbackRequest`
  - `reason: String?` (선택, 거부 이유 — 학습/통계 용도)

## 2. 응답 (Response)
- 성공: `204 No Content`
- 실패: 401 (인증), 403 (다른 사용자), 404 (없음), 409 (`SENTENCE_FEEDBACK_INVALID_TRANSITION`)

## 3. 비즈니스 로직 (요약)
1. `feedbackId` 로 Aggregate 로드, 본인 소유 검증
2. `SentenceFeedback.reject(reason, clock)` — 상태 전이 + `decidedAt` + `rejectionReason` 기록
3. persist
4. **`SentenceFeedbackRejected` 이벤트 Outbox 발행** (선택 — 학습 통계 용도, 발행 여부는 Open Item)

## 4. 데이터 의존
- DB read/write: `sentence_feedback` (diary 스키마)
- Kafka (Outbox, 선택): `SentenceFeedbackRejected`

## 5. 예외 케이스
- 다른 사용자 소유 → 403
- 없는 `feedbackId` → 404
- 이미 final 상태 → 409 (`SENTENCE_FEEDBACK_INVALID_TRANSITION`)

## 6. 암묵적 로직 (Implicit)
- REJECTED 사유는 AI 응답 품질 개선 학습 신호로 활용 가능
- REJECTED 자체는 점수에 영향 없음 (또는 매우 작게) — 정책은 platform-service

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] `reason` 의 enum 후보 vs 자유 텍스트
- [ ] REJECTED 이벤트 발행 여부 (학습 분석 가치 vs 트래픽 비용)

## 9. KEEP/DROP/FIX 분류
- 신규 PRD (proposed)
