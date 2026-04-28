---
api_id: diarychat.send
http_method: POST
path: /api/v1/diary-chatrooms/{roomId}/messages
auth: Y
controller: DiaryChatMessageController.kt
handler: send
status: mined
---

# POST /api/v1/diary-chatrooms/{roomId}/messages — 일기 채팅 메시지 전송

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `roomId: Long`
- Body: `DiaryChatDto.SendMessageRequest { text, audioUrl? }` (`@Valid`)

## 2. 응답 (Response)
- 성공: `201 Created` + `DiaryChatDto.ChatMessageResponse`

## 3. 비즈니스 로직 (요약)
1. `SendMessageCommand(roomId, authorUserId=userId, text, audioUrl)` 생성
2. `facade.send(...)` → 메시지 저장 + (AI 토글 ON 시) AI 응답 트리거 가능성.

## 4. 데이터 의존
- DB write: diary_chat_messages
- 외부 API: AI 모델 (AI assistant ON 시 자동 응답)
- 폴링/푸시: 다른 참여자 수신 채널 (Polling endpoint와 결합)

## 5. 예외 케이스
- 권한 없음 → 403
- 방 닫힘/존재하지 않음 → 404

## 6. 암묵적 로직 (Implicit)
- text와 audioUrl 양립 가능 — 둘 다 동시 허용 시 동작 미확인.
- AI 응답 자동 트리거는 `aiAssistantEnabled` 플래그에 따라.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] text/audio 동시 처리 정책
- [ ] AI 응답 비동기/동기 흐름

## 9. KEEP/DROP/FIX 분류

**KEEP+FIX** — 가장 큰 변경. [`decisions/diary/diarychat-domain-policy.md`](../../decisions/diary/diarychat-domain-policy.md) §8~§13 박제 적용.

### 핵심 변경

1. **AI 응답 = 동기** — send 응답에 사용자 메시지 + AI 응답 텍스트 둘 다 포함 (사용자 명시 — polling 너무 느림).
2. **STT = 서버 내부 동기 호출** — audio-only 입력 시 chat-service 게이트웨이 → ai-service.SpeechToText 변환 후 text 채움. 클라 책임 X.
3. **TTS = 별 endpoint lazy GET** — send 의 응답에는 audioUrl 미포함. 사용자가 재생 시점에 [`getMessageAudio.md`](getMessageAudio.md) 호출.

### 박제 매핑

| 항목 | 결정 | 박제 § |
|---|---|---|
| Path `roomId` 타입 | `Long` → **UUID** | §1 |
| 입력 모델 | `text?` (1..1000) / `audioUrl?` 둘 중 1+ 필수, 둘 다 빈 값 → 400 `text_or_audio_required` | §8 |
| `text` + `audioUrl` 동시 | text 우선 사용 (STT 미호출), audioUrl 보존 | §8 |
| `audioUrl` 형식 | http/https URL, userInfo 차단 | §8 |
| 업로드 모델 | **이미 업로드된 URL 만 받음** (presigned URL / 업로드 endpoint = Non-Goals) | §8 |
| STT 흐름 | audio-only 시 chat-service `AiAssistantService.TranscribeUserAudio` (가칭, **선행 필요**) → ai-service `SpeechToText`. 동기 | §9 |
| AI 응답 흐름 | 사용자 메시지 저장 → `GenerateChatResponse` 동기 (대화 히스토리 + 새 메시지) → assistant 메시지 row insert (text 채움, audioUrl=null lazy) | §10 |
| 응답 latency | text 입력: ~35s / audio 입력: STT (~5s) + LLM (~35s) ≈ 40s | §10 |
| `aiAssistantEnabled=false` | 사용자 메시지만 응답 (AI 응답 미생성) | §10 |
| AI status 매핑 | OK / FAILED / RATE_LIMITED — 모두 200 + 안내 메시지 row insert (5xx X) | §12 |
| 권한 | 참여자 only, 비참여자 → 404 (IDOR) | §4 |
| 비공개 일기 + 비작성자 | 404 | §3, §4 |
| 삭제된 방 | 404 | §16 |
| 응답 schema | `{ userMessage: ChatMessageResponse, assistantMessage: ChatMessageResponse? }` (assistantMessage 는 `aiAssistantEnabled=true` 시) | §13 |
| 응답 코드 | mined 의 201 → **200 OK** (다중 row insert + 단일 응답 — 201 Created 의미 부적절) | §13 |
| audio 재생 | 별 endpoint `getMessageAudio` lazy 호출 | §11 |

근거 — 사용자 명시:
> AI 응답도 동기로 진행한다. polling 으로 받으면 너무 느리기 때문 다음 poll 이 언제일지 모름.
> 사용자가 텍스트를 입력하면 저장 혹은 음성으로 입력하면 이걸 STT 로 변환해서 텍스트로 저장.

후속 (Open Questions §8 해소):
- text/audio 동시: text 우선 (STT 우회) + audioUrl 보존.
- AI 응답 동기 채택.

선행 필요 (D-a-4-contracts PR):
- `AiAssistantService.TranscribeUserAudio` (가칭) 메서드 신설.
- `GenerateChatResponse` 응답 `audio_url` 필드 추가 (LLM+TTS 통합 채택 시) 또는 별 `SynthesizeAudio` 메서드.

