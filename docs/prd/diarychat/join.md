---
api_id: diarychat.join
http_method: POST
path: /api/v1/diary-chatrooms/{roomId}/join
auth: Y
controller: DiaryChatRoomController.kt
handler: join
status: mined
---

# POST /api/v1/diary-chatrooms/{roomId}/join — 채팅방 입장

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `roomId: Long`

## 2. 응답 (Response)
- 성공: `200 OK` + `DiaryChatDto.ChatRoomResponse`

## 3. 비즈니스 로직 (요약)
1. `facade.join(roomId, userId)` → participants에 추가 (이미 있으면 idempotent 추정).

## 4. 데이터 의존
- DB write: diary_chat_participants

## 5. 예외 케이스
- 방 없음 → 404
- 입장 정원 초과 등 정책 위반 → 403/409 (정책 확인 필요)

## 6. 암묵적 로직 (Implicit)
- 멱등성 가정 — 이미 참여 중인 사용자가 재호출해도 정상 응답.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 비공개 일기 채팅방의 입장 권한
- [ ] 차단 사용자 처리

## 9. KEEP/DROP/FIX 분류

**KEEP+FIX** — 가장 큰 의미 변경. [`decisions/diary/diarychat-domain-policy.md`](../../decisions/diary/diarychat-domain-policy.md) §6 박제 적용.

### 핵심 변경 — `join` 의미 재해석

**기존 의미**: 단순 참여자 등록 (멱등)
**새 의미**: **방 입장 + AI 응답 트리거** — 매 호출마다 동기 LLM 호출 후 AI 메시지 1건 생성

| 호출 시점 | AI 동작 | 프롬프트 |
|---|---|---|
| 첫 입장 (메시지 0건 / 본 사용자 first join) | AI 메시지 1건 동기 insert | **welcome 프롬프트** (일기 본문 컨텍스트) |
| 재입장 (메시지 ≥1) | AI 메시지 1건 동기 insert | **일반 프롬프트** + 최근 N개 히스토리 컨텍스트 |
| `aiAssistantEnabled=false` | 참여자 등록만, AI 응답 미생성 | — |

| 항목 | 결정 | 박제 § |
|---|---|---|
| Path `roomId` 타입 | `Long` → **UUID** | §1 |
| 참여 자격 | 공개 일기 = 누구나 / 비공개 = 작성자 only (404 IDOR) | §3 |
| 멱등성 | 참여자 row insert 만 멱등 (이미 등록 시 `last_entered_at` UPDATE) — **AI 응답은 매 호출마다 새 메시지** (멱등 X) | §6 |
| LLM Deadline | chat-service `GenerateChatResponse` 35s | §6, [catalog](../../decisions/contracts/ai-assistant-service-method-catalog.md) |
| 응답 schema | `ChatRoomResponse` + AI 메시지 (`ChatMessageResponse`, type=ASSISTANT, text 채움, audioUrl=null lazy) | §6, §13 |
| FAILED 시 | 안내 메시지 row insert + 200 (validation FAILED 우회 정합) | §12 |
| audio 재생 | 별 endpoint `GET .../messages/{messageId}/audio` lazy 호출 | §11, [getMessageAudio.md](getMessageAudio.md) |

근거 — 사용자 명시:
> 웰컴 메세지는 최초만 생성되는 것이 아니라 어느 사용자가 방에 들어가면 항상 생성되어야 한다. 기존 다른 히스토리가 있는 상황에서 들어와도 AI 응답은 있어야 함. 다만 웰컴과는 다른 프롬프트를 사용해야하며 히스토리를 반영한 일반 AI 응답이 이루어져야 함.

응답 latency: ~35s (LLM). 클라 로딩 인디케이터 책임.

후속 (Open Questions §8 해소):
- 비공개 일기 입장 권한: 작성자 only (§3 박제).
- 차단 사용자: moderation 도메인 후속 (Non-Goals).

