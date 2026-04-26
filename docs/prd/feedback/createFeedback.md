---
api_id: feedback.createFeedback
http_method: POST
path: /api/v1/feedbacks
auth: Y
controller: FeedbackApiController.kt
handler: createFeedback
status: mined
---

# POST /api/v1/feedbacks — 피드백(문장 첨삭 등) 등록

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: `FeedbackDto.RegisterRequest` (⚠️ `@Valid` 누락)

## 2. 응답 (Response)
- 성공: `201 Created` + `FeedbackDto.RegisterResponse(feedbackInfo)`

## 3. 비즈니스 로직 (요약)
1. `request.toCommand(userId)` → `feedbackFacade.registerFeedback(command)` → 저장 (AI 호출 가능).

## 4. 데이터 의존
- DB write: feedbacks
- 외부 API: AI 모델 (첨삭) 가능성

## 5. 예외 케이스
- (validation 미적용) 잘못된 입력도 통과 가능 — `@FIX`

## 6. 암묵적 로직 (Implicit)
- 컨트롤러에 주석 처리된 `GET /{sentenceId}` 존재 → SentenceController의 `retrieveFeedback`이 대체.
- `@Valid` 누락 — 다른 register와 동일 패턴.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] sentenceId가 Body에 포함되는가
- [ ] 첨삭 결과 동기/비동기

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: `@Valid` 추가 → `@FIX`
