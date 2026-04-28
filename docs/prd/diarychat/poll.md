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

## 9. KEEP/DROP/FIX 분류

**KEEP+FIX** — [`decisions/diary/diarychat-domain-policy.md`](../../decisions/diary/diarychat-domain-policy.md) §15 박제 적용. 의미 축소 (제한적 사용).

### 사용 시점 명시 — 본인 send 후 AI 응답은 동기로 받으므로 polling 불요

| 시점 | polling 사용? |
|---|---|
| 본인 `send` 후 AI 응답 수신 | **불요** — send 가 동기로 사용자 메시지 + AI 응답 둘 다 반환 (§10) |
| 다른 사용자의 새 메시지 수신 | **사용** — 멀티 참여자 시나리오에 한정 |
| 첫 입장 시 히스토리 로드 | **불요** — `listMessages` 또는 `join` 응답 |

| 항목 | 결정 | 박제 § |
|---|---|---|
| Path `roomId` 타입 | `Long` → **UUID** | §1 |
| Query `after` 타입 | `Long` → **UUID** + opaque cursor (listMessages 정합) | §14, §15 |
| `wait` default | 25s (mined 유지) | §15 |
| `wait` max | **30s** (mined 미명시 부채 — 게이트웨이 idle timeout 보다 짧게) | §15 |
| `wait` 단위 | 초 (mined 추정 유지) | §15 |
| 구현 | Servlet async + `DeferredResult` (mined 유지) | §15 |
| 권한 | 참여자 only, 비참여자 → 404 (IDOR) | §4 |
| 비공개 일기 + 비작성자 | 404 | §3, §4 |
| 삭제된 방 | 404 | §16 |
| 응답 (wait 만료) | 빈 결과 정상 응답 (mined 유지) | §15 |
| 응답 schema | listMessages 와 동일 `ChatMessageResponse` 배열 + cursor | §13, §14 |
| **WebSocket / SSE 전환** | **Non-Goals (사용자 명시)** — 트래픽 증가 시점에도 재검토 보류 | §15 |

근거 — 사용자 명시:
> websocket 은 현재 고려사항이 아니야. 이 방의 비즈니스적 의미는 실시간이 아니고 방에 들어갔을 때 직전까지의 대화 내용을 보고 본인도 답변을 하는 것이 목표.

본 도메인이 실시간 X 라 polling 자체가 부수적 — endpoint 보존하되 사용 시점 매우 제한적.

후속 (Open Questions §8 해소):
- WebSocket 전환: 명시적 거부 (재검토 X).
- wait 상한: 30s 박제.
- 게이트웨이 idle timeout 정합성: D-a-4-impl-presentation 시점 (Servlet async 설정).

