---
api_id: event.createEvent
http_method: POST
path: /api/v1/events
auth: Y
controller: EventController.kt
handler: createEvent
status: mined
---

# POST /api/v1/events — 이벤트 발행 (Kafka 프록시)

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: 원시 JSON 문자열 (`@RequestBody message: String?`)

## 2. 응답 (Response)
- 성공: `200 OK` + `"Message sent to Kafka"` (plain text)
- 실패: 400 (`EventBadRequestException`)

## 3. 비즈니스 로직 (요약)
1. message null 체크 → 400
2. `processEvent`: `objectMapper.readTree(message)`로 `eventType` 필드 추출
3. `eventClassMap` (WordReviewEvent / WordKnownEvent / FeedbackSentenceEvent) 조회
4. 매핑되면 `producer.send("events", event)` → Kafka topic `events`로 발행
5. 매핑 안 되면 `EventBadRequestException`

## 4. 데이터 의존
- Kafka: produce → topic `events`
- (코드 주석상) MongoDB 저장 — 미구현

## 5. 예외 케이스
- message null → 400
- 알 수 없는 eventType → 400

## 6. 암묵적 로직 (Implicit)
- **클라이언트가 임의 JSON을 Kafka로 직접 흘리는 구조** — 보안/스키마 검증 우회 위험.
- `println` 디버그 출력 — 운영 코드 잔존(`@DROP`).
- 주석에 "mongo db 저장" / "event 처리 로직" — 미구현 흔적.
- 토픽 이름이 `"events"`로 하드코딩 (env 분리 안 됨).

## 7. 호출자 (Clients)
- 모바일 (학습 진행 등 사용자 행동 기록)

## 8. TODO / Open Questions
- [ ] 이 endpoint의 존재 자체 재검토 — 클라이언트가 Kafka를 직접 흘리는 패턴이 적절한가
- [ ] 토픽 이름 환경 분리

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: 디버그 println **`@DROP`**, 주석 잔존 **`@DROP`**, 토픽 하드코딩 **`@FIX`**, endpoint 존재 자체 재검토 (가장 강력한 `@DROP` 후보)
