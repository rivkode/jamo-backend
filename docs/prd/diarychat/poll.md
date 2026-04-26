---
api_id: diarychat.poll
http_method: GET
path: /api/v1/diary-chatrooms/{roomId}/messages/poll
auth: Y
controller: DiaryChatPollingController.kt
handler: poll
status: mined
---

# GET /api/v1/diary-chatrooms/{roomId}/messages/poll — 롱폴링 (DeferredResult)

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `roomId: Long`
- Query: `after?: Long (default 0)` — 마지막 본 메시지 ID
- Query: `wait?: Int (default 25)` — 대기 시간(초)

## 2. 응답 (Response)
- 성공: `200 OK` + `DiaryChatPollView` (Spring `DeferredResult`로 비동기)

## 3. 비즈니스 로직 (요약)
1. `facade.poll(roomId, userId, after, wait)` → 새 메시지가 도착할 때까지(또는 wait 만료) 대기 후 응답.

## 4. 데이터 의존
- DB read: 신규 메시지 polling
- 인메모리 동기화: `DeferredResult` 보관소(facade 내부)

## 5. 예외 케이스
- 권한 없음 → 403/404
- wait 만료 → 빈 결과 (정상 응답)

## 6. 암묵적 로직 (Implicit)
- **롱폴링** — Servlet async 사용. 동시 연결 수가 worker 스레드를 잠식하지 않는지 설정 확인.
- wait 단위(초로 추정)는 facade 구현 의존.

## 7. 호출자 (Clients)
- 모바일/웹 (실시간성)

## 8. TODO / Open Questions
- [ ] WebSocket/SSE로의 전환 검토
- [ ] wait 상한
- [ ] 게이트웨이/프록시 idle timeout과의 정합성

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: WebSocket 전환 → `@FIX` (대규모 시 폴링 비용)
