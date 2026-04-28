---
api_id: diarychat.getMessageAudio
http_method: GET
path: /api/v1/diary-chatrooms/messages/{messageId}/audio
auth: Y
controller: DiaryChatMessageController
handler: getMessageAudio
status: proposed
---

# GET /api/v1/diary-chatrooms/messages/{messageId}/audio — 메시지 음성 (TTS lazy 생성)

## 0. 배경

채팅 메시지의 음성은 **lazy 생성** 한다. 사용자가 재생 시점에만 TTS 호출하고, 한 번 생성된 audioUrl 은 메시지 row 의 `audio_url` 컬럼에 캐시한다.

기존 4 endpoint 모델 (`/welcome`, `/tts`, `/stt`, `/chat`) 의 textId 기반 lazy audio 캐시 친화 패턴을 본 endpoint 1개로 흡수.

자세한 설계 근거 → [`decisions/diary/diarychat-domain-policy.md` §11](../../decisions/diary/diarychat-domain-policy.md).

## 1. 요청 (Request)

- Header: `@LoginUser`
- Path: `messageId: UUID`

## 2. 응답 (Response)

- 성공 (200 OK):

```json
{
  "messageId": "UUID",
  "audioUrl": "https://...",
  "expiresAt": "2026-04-28T10:30:00Z"
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `messageId` | UUID | echo |
| `audioUrl` | String | http/https URL. presigned URL 일 수 있음 |
| `expiresAt` | Instant? | presigned URL 만료 시각. nullable (영구 URL 시 null) |

## 3. 비즈니스 로직 (요약)

1. `messageId` 로 메시지 row 조회. 없으면 404.
2. 권한 가드: 해당 메시지가 속한 방의 참여자인지 확인. 비참여자 / 비공개 일기의 비작성자 → 404 (IDOR).
3. 삭제된 방 (`chatrooms.deleted_at IS NOT NULL`) → 404.
4. `messages.audio_url` 이 NOT NULL → 캐시 hit, 즉시 응답.
5. NULL → chat-service 게이트웨이 호출 (TTS):
   - **선행 필요 contracts 결정** (D-a-4-contracts PR):
     - 옵션 A: `AiAssistantService.GenerateChatResponse.audio_url` 통합 (assistant 메시지 시점에 같이 생성) — 본 endpoint 는 단순 조회만
     - 옵션 B: `AiAssistantService.SynthesizeAudio` (가칭) 별 메서드 — 본 endpoint 가 호출
   - 본 PR (D-a-4-eval) 시점 contracts 변경 0. 후속 PR 에서 박제.
6. 응답으로 받은 audioUrl 을 `messages.audio_url` 에 채움 (UPSERT).
7. `{ messageId, audioUrl, expiresAt }` 응답.

## 4. 데이터 의존

- DB read/write: `chat_messages` (audio_url 컬럼 lazy 채움)
- DB read: `chatrooms` (deleted_at 가드), `chat_participants` (권한 가드)
- 외부 gRPC: chat-service `AiAssistantService` (TTS 게이트웨이 — 선행 필요)

## 5. 예외 케이스

| 시나리오 | HTTP |
|---|---|
| 메시지 없음 | 404 |
| 비참여자 | 404 (IDOR) |
| 비공개 일기 + 비작성자 | 404 |
| 방 soft-delete 됨 | 404 |
| TTS gRPC FAILED (chat-service) | 503 또는 200 + `audioUrl: null` (선행 결정 — D-a-4-impl-app) |
| TTS gRPC `RATE_LIMITED` | 429 (선행 결정) |
| TTS Deadline 초과 | 504 |
| 메시지 `text` 가 null (STT 미완료 / FAILED) | 422 (TTS 입력 부재) |

## 6. 암묵적 로직 (Implicit)

- **캐시 친화** — 두 번째 호출은 DB row 캐시로 즉시 응답 (TTS 호출 0).
- **CDN / presigned URL** — chat-service 가 반환한 audioUrl 의 모델 (S3 presigned / CDN 직접 / 영구 URL) 은 contracts / chat-service 구현 책임.
- **권한 기준은 메시지 단건이 아니라 속한 방** — 비참여자가 messageId 만 알아도 접근 불가.

## 7. 호출자 (Clients)

- 모바일 / 웹 (사용자가 메시지 재생 버튼 누를 때 또는 백그라운드 prefetch).

## 8. TODO / Open Questions

- [ ] presigned URL 만료 시 재생성 정책 (자동 갱신 vs 클라가 401 수신 후 재요청) — D-a-4-impl-infra 시점.
- [ ] 사용자 메시지의 audio (사용자가 audioUrl 첨부한 경우) 는 그대로 echo, text-only 사용자 메시지의 TTS 생성 여부 — 본 endpoint 책임 명확화 (잠정: assistant 메시지 + text-only 사용자 메시지 모두 TTS 가능).
- [ ] TTS 비용 quota (사용자별 분당 N회) — D-a-4-contracts 시점.
- [ ] FAILED 시 응답 코드 (200 + null vs 5xx) — D-a-4-impl-app 시점.
- [ ] audio prefetch / 자동 생성 정책 (현 시점 lazy only) — 별 PR.

## 9. KEEP/DROP/FIX 분류

**KEEP (proposed)** — [`decisions/diary/diarychat-domain-policy.md`](../../decisions/diary/diarychat-domain-policy.md) §11 / §17 / §18 박제로 신설.

근거:
- mined 4 endpoint 모델 (`/tts`) 을 본 endpoint 1개로 흡수 + textId → messageId UUID 정합.
- audio lazy 생성 + DB 캐시 = TTS 비용 최소 + CDN 친화.
- 동기 send 응답에 audio 포함 시 latency ~45s+ → 분리하여 사용자 재생 시점에만 호출.
