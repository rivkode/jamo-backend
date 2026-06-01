# Decision: diarychat 도메인 — 워크플로 / endpoint 모델 / STT·TTS / Saga / 응답 schema

> ⚠️ **부분 SUPERSEDED (2026-06-01)** — 프론트가 실제 동작하는 `API_SPEC.md` 부록 E.2 가 본 문서의 핵심
> 항목(§1 ID=UUID → int64, §7 leave DROP → 구현, §9 STT 서버측 → 클라, §10 AI 동기 → 롱폴)을 폐기한다.
> 구현 기준은 [`diarychat-domain-policy-v2-apispec-e.md`](diarychat-domain-policy-v2-apispec-e.md) 를 따른다.
> 본 문서는 history 보존용.

- **상태**: Accepted
- **결정일**: 2026-04-28
- **결정자**: jonghun
- **PR**: PR D-a-4-eval (`feature/diary-prd-evaluation-diarychat`)
- **관련 PRD**: [`prd/diarychat/create.md`](../../prd/diarychat/create.md), [`get.md`](../../prd/diarychat/get.md), [`join.md`](../../prd/diarychat/join.md), [`leave.md`](../../prd/diarychat/leave.md) (DROP), [`listParticipants.md`](../../prd/diarychat/listParticipants.md), [`aiToggle.md`](../../prd/diarychat/aiToggle.md), [`send.md`](../../prd/diarychat/send.md), [`listMessages.md`](../../prd/diarychat/listMessages.md), [`poll.md`](../../prd/diarychat/poll.md), [`getMessageAudio.md`](../../prd/diarychat/getMessageAudio.md) (proposed, 신규)
- **관련 결정**: [`decisions/diary/diary-domain-policy.md`](diary-domain-policy.md) (Visibility / 404 IDOR / DiaryDeleted Saga), [`decisions/diary/comment-domain-policy.md`](comment-domain-policy.md) (UUID / 404 통일 / Outbox), [`decisions/diary/validation-ai-fallback-policy.md`](validation-ai-fallback-policy.md) (FAILED 우회 / userId propagation), [`decisions/contracts/ai-assistant-service-method-catalog.md`](../contracts/ai-assistant-service-method-catalog.md) (`GenerateChatResponse` Deadline 35s, status `OK/FAILED/RATE_LIMITED`), [`decisions/contracts/ai-service-method-signatures.md`](../contracts/ai-service-method-signatures.md) (`SpeechToText` / `TextToSpeech`)
- **관련 contracts (선행 필요 — D-a-4-contracts PR)**:
  - `AiAssistantService.TranscribeUserAudio` (가칭) — STT 게이트웨이 메서드 신설
  - `AiAssistantService.GenerateChatResponse` 응답 `audio_url` 필드 추가 (LLM + TTS 통합) **또는** chat-service 가 LLM → TTS 직렬 호출 후 응답 합성 — D-a-4-contracts 시점 결정
- **관련 ADR**: [ADR-0003 AI 호출 아키텍처](../../adr/0003-ai-call-architecture.md), [ADR-0007 contracts-first 병렬 개발](../../adr/0007-contracts-first-parallel-development.md)

## 컨텍스트

`diarychat` 은 diary-service 안의 sub-도메인. 일기 단위 채팅방 + AI 어시스턴트 자동 응답이 핵심. 9 mined PRD (`create / get / join / leave / listParticipants / aiToggle / send / listMessages / poll`) 가 모두 §9 미평가 상태.

본 평가의 본질은 **AI 워크플로의 endpoint 분할 모델 결정**:

- **이전 구현**: 4 endpoint 분리 (`/welcome`, `/tts`, `/stt`, `/chat`) — 클라가 단계별 호출 + textId 기반 audio lazy 요청.
- **본 결정**: 클라 RTT 단순화 위해 **3 endpoint 통합** (`/join`, `/messages` (send), `GET .../messages/{messageId}/audio`).

비즈니스 의미는 **실시간 채팅 X** — 사용자가 방에 들어왔을 때 직전 대화 + AI 응답 1건이 새로 나오고, 본인도 답변하는 모델. 따라서 WebSocket / SSE 미도입.

음성 입출력은 **필수 기능** — LLM 응답은 TTS 로 audioUrl, 사용자 입력은 audioUrl-only 시 STT 로 text 변환.

## 비즈니스 워크플로

```
[사용자 진입]
  POST /diary-chatrooms             ← 누구나 createOrGet (멱등)
  POST /diary-chatrooms/{id}/join   ← 매 입장마다 호출 (참여자 등록 + AI 응답 트리거)
                                       └─ 첫 입장 (히스토리 0): welcome 프롬프트
                                       └─ 재입장 (히스토리 ≥1): 일반 프롬프트 + 히스토리 컨텍스트
                                       └─ 동기 LLM 호출 (~35s) → AI 메시지 1건 동기 insert
                                       └─ 응답에 ChatRoomResponse + AI 메시지 텍스트 포함

[사용자 발화]
  POST /diary-chatrooms/{id}/messages  ← send (text 또는 audioUrl 또는 둘 다)
                                          └─ audio-only 시 STT 동기 변환 → text 채움
                                          └─ 사용자 메시지 row insert
                                          └─ AI 응답 동기 (LLM ~35s) → AI 메시지 row insert
                                          └─ 응답에 [사용자 메시지, AI 응답 텍스트] 둘 다 포함

[음성 재생 시점]
  GET /diary-chatrooms/messages/{messageId}/audio  ← lazy TTS
                                          └─ 첫 호출: chat-service → ai-service.TextToSpeech
                                          └─ messages.audio_url 캐시
                                          └─ 두 번째 이후: 캐시된 audioUrl 즉시 반환
```

## endpoint 모델 분석 (4 → 3 통합)

### 이전 4 endpoint (textId 기반 lazy)

| step | endpoint | 입출력 |
|---|---|---|
| 1 | `/welcome` | roomId → text + textId |
| 2 | `/tts` | textId → audioUrl |
| 3 | `/stt` | audioUrl → text + textId |
| 4 | `/chat` | text → text + textId |

### 본 결정 — 3 endpoint 통합

| 새 endpoint | 통합한 이전 step | 동기 호출 체인 |
|---|---|---|
| `POST /diary-chatrooms/{id}/join` | `/welcome` (welcome 프롬프트 분기로 일반화) + `/chat` (재입장 시) | LLM 35s |
| `POST /diary-chatrooms/{id}/messages` (send) | `/stt` (audio-only 입력 시 서버 내부 호출) + `/chat` (사용자 입력 후 AI 응답) | (STT 5s) + LLM 35s |
| `GET /diary-chatrooms/messages/{messageId}/audio` (신규) | `/tts` lazy | TTS 5s (첫 호출만) |

### 채택 근거

- **클라 RTT 4 → 2** (send + audio GET. audio 는 prefetch 가능).
- `/welcome` 과 `/chat` 모두 LLM 호출 — 분리 이득 없음. `/join` 의 첫 호출 케이스 = welcome, 재입장 = 일반 응답으로 일반화.
- **STT 는 서버 내부 호출** (클라 책임 → 서버 책임 이전) — 클라가 STT/LLM 단계 머신 들고 있을 필요 없음.
- **TTS 만 lazy GET 분리 유지** — audio 는 CDN/presigned URL 캐시 친화 (별 endpoint), 텍스트는 빨리 받아 즉시 표시 (UX 분리). 메시지 row 의 `audio_url` 컬럼 = 첫 호출 시 채움 + 캐시.
- **부분 실패 격리**: send 의 LLM 단계 실패 = `status=FAILED` 안내 메시지 row, audio 단계 실패 = 텍스트는 이미 보유 (재생만 실패).

### 거부한 옵션

| 옵션 | 거부 이유 |
|---|---|
| `/welcome /tts /stt /chat` 4 endpoint 그대로 KEEP | 클라 단계 머신 복잡, RTT 4회, 단계 간 일관성 보장 어려움 |
| send 응답에 audioUrl 까지 동기 포함 (TTS 도 send 안에) | latency ~45s+ 누적, 재생 시점 아닐 때 TTS 비용 낭비 |
| AI 응답 비동기 (polling) | 사용자 명시 거부 — polling 주기 30s 일 수도 있어 너무 느림 |
| WebSocket / SSE | 사용자 명시 거부 — 본 도메인은 실시간 X (직전 히스토리 + 답변 모델) |

## 결정

### 1. ID 타입 — UUID 일관

| 자원 | 타입 |
|---|---|
| `roomId` | UUID (BINARY(16)) |
| `messageId` | UUID |
| `userId` (참여자 / 발신자) | UUID (identity-service 정합) |
| `diaryId` | UUID |

mined PRD 의 `Long` 표기 모두 폐기. profile (#39) / comment (#50) / diary (#52) 평가 정합.

### 2. 방 생성 — 누구나, 일기당 단일 방

| 항목 | 결정 |
|---|---|
| 핸들러 | `createOrGet(diaryId, requesterUserId, aiAssistantEnabled)` 멱등 |
| 권한 | **누구나** (작성자 제한 X — 사용자 명시) |
| 응답 코드 | 200 (생성·조회 통합 — 201 X) |
| 일기 ↔ 방 | **1:1** (다중 방 거부 — Non-Goals) |
| `aiAssistantEnabled` 초기값 | 클라 요청 default = true |
| 방장 | **별도 개념 미도입** (작성자 = 일기 작성자 만 aiToggle 권한) |

근거:
- `createOrGet` 가 멱등 → 중복 생성 안전.
- 다중 방은 운영 / UX 복잡도 ↑ — 일기 댓글 thread 와 다른 별 도메인 됨. 미채택.
- "방장" 개념 도입 시 owner 위임 / 강퇴 등이 필요해 도메인 팽창. aiToggle 권한만 일기 작성자 한정으로 충분.

### 3. 참여 자격 — diary Visibility 정합

| 일기 visibility | join 권한 |
|---|---|
| PUBLIC | 누구나 join |
| PRIVATE | **작성자 only** (비작성자 → 404 IDOR, [diary-domain-policy §3](diary-domain-policy.md#2-공개비공개-정책--visibility-enum)) |

비공개 일기의 모든 diarychat endpoint 는 비작성자에게 404 (자원 존재 비노출).

### 4. 권한 가드 — 404 통일

| 시나리오 | HTTP |
|---|---|
| 방 없음 / 메시지 없음 | 404 |
| 비참여자 호출 (get / listParticipants / send / listMessages / poll / getMessageAudio) | **404** (403 미사용, IDOR 보호) |
| 비공개 일기 + 비작성자 모든 endpoint | 404 |
| 삭제된 방 (deleted_at NOT NULL) | 404 |

[diary-domain-policy §3](diary-domain-policy.md#2-공개비공개-정책--visibility-enum) / [comment-domain-policy §4](comment-domain-policy.md#4-404-통일-idor-보호) 정합.

### 5. aiToggle — 작성자 only, 명시적 boolean 멱등

| 항목 | 결정 |
|---|---|
| 핸들러 | `setAiAssistant(roomId, userId, enabled: Boolean)` |
| 권한 | **일기 작성자 only** (비작성자 → 404) |
| 응답 | `200 OK` + `ChatRoomResponse` |
| 멱등성 | `enabled=true/false` 동일값 재호출 안전 |

근거: comment-domain-policy `setLiked(boolean)` 패턴 정합. 명시적 boolean 으로 클라 retry 안전.

### 6. AI 응답 트리거 — `join` 매 호출

| 호출 시점 | AI 응답 동작 |
|---|---|
| 첫 입장 (해당 방에 메시지 0건 + 본 사용자 첫 join) | **welcome 프롬프트** (일기 본문 컨텍스트) → LLM → AI 메시지 1건 동기 insert |
| 재입장 (메시지 ≥1) | **일반 프롬프트** + 최근 N개 히스토리 컨텍스트 → LLM → AI 메시지 1건 동기 insert |
| `aiAssistantEnabled=false` | AI 응답 미생성 (참여자 등록만) |

| 항목 | 결정 |
|---|---|
| 호출 멱등성 | 참여자 row insert 만 멱등 (이미 등록 시 update `last_entered_at`) — **AI 응답은 매 호출마다 새 메시지** (멱등 X) |
| LLM Deadline | chat-service `GenerateChatResponse` 35s ([catalog](../contracts/ai-assistant-service-method-catalog.md)) |
| 응답 schema | `ChatRoomResponse` + AI 메시지 (`ChatMessageResponse`, type=ASSISTANT, text 포함, audioUrl=null lazy) |
| FAILED 시 | 안내 메시지 row insert ("지금은 답변할 수 없어요"), 200 응답 (validation FAILED 우회 정책 정합) |

근거 — 사용자 명시:
> 웰컴 메세지는 최초만 생성되는 것이 아니라 어느 사용자가 방에 들어가면 항상 생성되어야 한다.
> 기존 다른 히스토리가 있는 상황에서 들어와도 AI 응답은 있어야 함. 다만 웰컴과는 다른 프롬프트를 사용해야하며 히스토리를 반영한 일반 AI 응답이 이루어져야 함.

### 7. leave PRD — DROP

mined PRD `leave.md` 는 폐기. 사용자 명시:
> 나간다는 요구사항은 없어.

| 항목 | 결정 |
|---|---|
| 분류 | DROP |
| 파일 처리 | §9 에 DROP 표기 + 파일 보존 (history) |
| HTTP endpoint | 미구현 (`POST /leave` 미배포) |
| 방 종료 행위 | 사용자가 명시적으로 leave 하지 않음 — DiaryDeleted Saga cascade 만이 방을 종료 (soft-delete) |

### 8. send 입력 모델 — text/audio 양립

| 입력 조합 | 처리 |
|---|---|
| `text` only (1..1000) | text 그대로 저장, audioUrl=null |
| `audioUrl` only | **STT 동기 호출** → 변환된 text 저장 + audioUrl 보존 |
| `text` + `audioUrl` 둘 다 | text 우선 사용 (STT 미호출), audioUrl 그대로 보존 |
| 둘 다 빈 값 | 400 `text_or_audio_required` |

| 항목 | 결정 |
|---|---|
| `text` 길이 | 1..1000 (validation 도메인의 1..2000 보다 짧음 — 채팅 단위) |
| `audioUrl` 형식 | http/https URL — userInfo 차단 (avatar 정합) |
| `audioUrl` 업로드 모델 | **이미 업로드된 URL 만 받음** (presigned URL / 업로드 endpoint = Non-Goals, 별 PR) |

근거:
- text+audio 양립 — 클라가 자체 STT 미리 한 케이스 (모바일 OS STT 등) 수용.
- 업로드 endpoint 분리는 별 도메인 (file/upload) — 본 PR 시점 scope 외.

### 9. STT 흐름 — chat-service 게이트웨이 (선행 필요)

```
diary-service send (audioUrl-only)
  → chat-service AiAssistantService.TranscribeUserAudio (가칭, 선행 필요)
    → ai-service AiService.SpeechToText (4MB unary, 박제됨)
  ← 변환된 text
사용자 메시지 row insert (text + audioUrl 둘 다 보존)
```

| 항목 | 결정 |
|---|---|
| 게이트웨이 | chat-service `AiAssistantService` 신규 메서드 (가칭 `TranscribeUserAudio`) — **contracts 선행 필요** (D-a-4-contracts PR) |
| Deadline | 10s (잠정 — D-a-4-contracts 시점 catalog 박제) |
| status 카탈로그 | `OK / FAILED` (잠정 — RATE_LIMITED 도입은 운영 후 결정) |
| 실패 시 | `status=FAILED` → 사용자 메시지 row 에 text=null + audioUrl 만 저장 + 안내 표시 (200) |
| 비용 / quota | 사용자별 분당 30회 (잠정) |

근거:
- ADR-0003: 모든 AI 호출은 chat-service 경유. diary-service 가 ai-service 직접 호출 X.
- 별 메서드 = 메서드 단위 Deadline / quota / metrics 격리 ([catalog](../contracts/ai-assistant-service-method-catalog.md) Option B 일관).

### 10. AI 응답 동기 흐름 — send 응답에 둘 다 포함

| 단계 | 동작 |
|---|---|
| 1 | 사용자 메시지 저장 (text 채워진 상태, audioUrl optional) |
| 2 | chat-service `GenerateChatResponse` 동기 호출 (대화 히스토리 N개 + 새 메시지) — Deadline 35s |
| 3 | 응답 text → AI 메시지 row insert (type=ASSISTANT, text 채움, audioUrl=null lazy) |
| 4 | send 응답에 [사용자 메시지, AI 메시지] 둘 다 포함 |

| 항목 | 결정 |
|---|---|
| 응답 latency | text-only 입력: ~35s / audio 입력: STT (~5s) + LLM (~35s) ≈ 40s |
| AI 응답 보장 | `aiAssistantEnabled=true` 일 때만. false 시 사용자 메시지만 응답 |
| 히스토리 윈도우 | 최근 20 메시지 (chat-service 측, 잠정 — 운영 모니터링 후 결정) |
| FAILED 시 | 안내 메시지 row insert + 200 응답 (validation 정합) |
| 클라 UX | 로딩 인디케이터 책임 (~35s 응답) |

근거 — 사용자 명시:
> AI 응답도 동기로 진행한다. polling 으로 받으면 너무 느리기 때문 다음 poll 이 언제일지 모름. 30초 뒤일 수도 있음.

### 11. TTS lazy GET — 신규 endpoint

`GET /api/v1/diary-chatrooms/messages/{messageId}/audio`

| 단계 | 동작 |
|---|---|
| 1 | 메시지 row 조회 (404 가드: 방 없음 / 메시지 없음 / 권한 없음) |
| 2 | `messages.audio_url` 이 NOT NULL → 즉시 반환 (캐시 hit) |
| 3 | NULL → chat-service 게이트웨이 → ai-service `TextToSpeech` 동기 호출 |
| 4 | 받은 audioUrl 을 `messages.audio_url` 채움 + 응답 |

| 항목 | 결정 |
|---|---|
| 응답 schema | `{ messageId: UUID, audioUrl: String, expiresAt: Instant? }` |
| 권한 | 메시지가 속한 방의 참여자 only (비참여자 404) |
| 캐싱 | DB 컬럼 캐시 (presigned URL 만료 시 재생성은 후속) |
| TTS 게이트웨이 | chat-service `AiAssistantService.GenerateChatResponse` 응답에 audio_url 포함 채택 시 **별 메서드 불요** — D-a-4-contracts 시점 결정. 그 외에는 `SynthesizeAudio` (가칭) 신규 |
| 사용자 메시지 audio | 사용자가 audioUrl 첨부했으면 그 URL 그대로 반환. text-only 사용자 메시지의 audio = TTS 동일 흐름 (옵션) |

근거:
- audio 는 사용자가 재생 시점에만 필요 — eager 생성은 비용 낭비.
- DB 캐시 = 두 번째 호출 즉시 반환, CDN/presigned URL 친화.
- 4 endpoint 분리의 textId 기반 audio 캐시 친화성을 유지.

### 12. AI status 매핑 — catalog 정합

[`AiAssistantService.GenerateChatResponse`](../contracts/ai-assistant-service-method-catalog.md) status 박제:

| status | 처리 |
|---|---|
| `OK` | AI 메시지 row insert (text), 정상 응답 |
| `FAILED` | 안내 메시지 row insert ("지금은 답변할 수 없어요") + 200 |
| `RATE_LIMITED` | 안내 메시지 row insert ("한도 초과, 잠시 후") + 200 |

5xx 매핑 X (validation FAILED 우회 정합 — 사용자 흐름 차단 지양).

### 13. 응답 schema

#### `ChatRoomResponse` (6 필드)

```
{
  roomId: UUID,
  diaryId: UUID,
  authorId: UUID,                  // 일기 작성자 (= aiToggle 권한자)
  aiAssistantEnabled: Boolean,
  participantCount: Int,
  createdAt: Instant
}
```

#### `ChatMessageResponse` (8 필드)

```
{
  messageId: UUID,
  roomId: UUID,
  authorId: UUID?,                 // null 이면 ASSISTANT
  authorDisplayName: String,       // ASSISTANT 시 "AI 어시스턴트" 고정 / USER 시 BatchGetUserSummaries 조립
  type: USER | ASSISTANT,
  text: String?,                   // STT 미완료 / FAILED 시 null 가능
  audioUrl: String?,               // lazy GET 미수행 시 null
  createdAt: Instant
}
```

#### `ParticipantItem` (3 필드)

```
{
  userId: UUID,
  displayName: String,             // BatchGetUserSummaries (PR #35) 일괄 조립
  joinedAt: Instant
}
```

list 응답은 `BatchGetUserSummaries` (PR #35, 최대 200) 일괄 호출로 조립.

### 14. listMessages 페이징

| 항목 | 값 |
|---|---|
| `before` cursor | `(created_at, message_id)` base64 (UUID, opaque) |
| `size` default | 30 |
| `size` max | 100 |
| 정렬 | 작성일 내림차순 (최근부터 역방향, 스크롤 업 UX) |
| 응답 | `{ items: List<ChatMessage>, nextCursor: String?, hasMore: Boolean }` |
| 삭제 메시지 | 미지원 (Non-Goals — soft-delete X / hard-delete X 본 PR 시점) |
| audioUrl | lazy 미생성 시 null. 클라가 필요 시 GET .../audio 호출 |

mined PRD 의 `Long before` → UUID base64 cursor 로 통일 (comment / diary 정합).

### 15. poll 정책 — KEEP, 제한적 사용

| 항목 | 결정 |
|---|---|
| 분류 | KEEP+FIX |
| 사용 시점 | 다른 사용자의 메시지 수신 시 (현 시점 다중 참여자 시나리오 미흔함) — 본 사용자의 send 후 AI 응답은 동기로 받으므로 polling 불요 |
| `wait` default | 25s |
| `wait` max | 30s (게이트웨이 idle timeout 보다 짧게) |
| 구현 | Servlet async + DeferredResult |
| WebSocket / SSE | **Non-Goals** (사용자 명시) |

근거:
- 본 도메인 = 실시간 X. 사용자가 방에 들어왔을 때 직전 히스토리만 보면 충분.
- send 의 AI 응답은 동기 → polling 미필요.
- 다른 사용자가 동시 참여한 상황에서 그 사용자의 메시지 수신은 polling 으로 — 현 시점 multi-user 시나리오 흔하지 않으므로 endpoint 만 보존.

### 16. 방 삭제 — soft-delete + DiaryDeleted Saga cascade

[diary-domain-policy §9](diary-domain-policy.md#9-삭제--hard-delete--diarydeleted-saga-cascade) 의 chat-service 구독자 동작 박제:

| 항목 | 결정 |
|---|---|
| 일기 삭제 (`DiaryDeleted` 발행) | chat-service / diary-service 의 chatroom 영역이 구독 |
| chatroom 처리 | **soft-delete** (`chatrooms.deleted_at` 채움) — 사용자 명시 |
| 메시지 처리 | 보존 (감사 / 운영 목적) |
| 참여자 처리 | 보존 |
| 삭제된 방 endpoint | 모든 endpoint 404 (자원 부재로 간주) |
| 멱등성 | `ProcessedEvent` 테이블 — 같은 eventId 중복 처리 차단 ([CLAUDE.md](../../../CLAUDE.md) Kafka Consumer 멱등 의무) |

근거 — 사용자 명시:
> 채팅방을 삭제한다면 hard delete 가 아니고 그냥 soft delete 로 진행해도 돼.

다른 도메인 (diary / comment) 의 hard-delete 와 다름. chatroom 만 soft-delete 채택 (운영 / 감사 목적 + 메시지 보존 자연성).

### 17. 선행 필요 contracts (D-a-4-contracts PR 에서 일괄 박제)

| 항목 | 영향 |
|---|---|
| `AiAssistantService.TranscribeUserAudio` (가칭) 신규 메서드 | STT 게이트웨이 |
| `GenerateChatResponse` 응답 `audio_url` 필드 추가 | LLM + TTS 통합 응답 |
| (대안) `AiAssistantService.SynthesizeAudio` (가칭) 신규 메서드 | TTS 단독 게이트웨이 — `GenerateChatResponse` audio 통합 거부 시 채택 |

D-a-4-contracts PR 에서 (a) `GenerateChatResponse.audio_url` 통합 채택 vs (b) 별 메서드 채택 결정. 후자 시 lazy GET endpoint 의 chat-service 호출 흐름이 별 메서드.

[`ai-assistant-service-method-catalog.md`](../contracts/ai-assistant-service-method-catalog.md) 갱신 + chat.proto 변경 + ai-service-method-signatures.md 정합 확인.

본 평가 PR (D-a-4-eval) 시점 contracts 변경 0. 본 PR 본문에 후속 PR 시리즈 명시.

### 18. 신규 endpoint 추가 — `getMessageAudio`

[`prd/diarychat/getMessageAudio.md`](../../prd/diarychat/getMessageAudio.md) (proposed, 신규) 추가.

```
GET /api/v1/diary-chatrooms/messages/{messageId}/audio
auth: Y
응답: 200 + { messageId, audioUrl, expiresAt? }
```

mined 9 PRD + 신규 1 PRD = **10 endpoint**. `_status.md` diarychat 행 9 → 10 (proposed +1) 갱신.

### 19. 방 생성 권한 — 누구나 (mined 추정 폐기)

| 항목 | 결정 |
|---|---|
| `createOrGet` 권한 | **누구나** (사용자 명시) |
| 비공개 일기의 비작성자 호출 | 404 (참여 자격 정합 — 비공개 일기는 참여 자격 자체 없음) |
| 공개 일기의 비작성자 호출 | 200 + 기존 방 반환 (또는 신규 생성) |

mined PRD 의 "일기 작성자만 생성" 추정은 사용자 명시로 폐기.

## 검토한 옵션 (요약)

### Option A. 4 endpoint (`/welcome /tts /stt /chat`) KEEP — 거부

장점:
- audio 별 endpoint = CDN 친화 (textId 기반 캐시).
- 부분 실패 격리 (welcome 실패가 chat 흐름 차단 X).
- 단계별 진행 표시 UX 가능.

단점 (거부 이유):
- 클라 단계 머신 4 RTT (welcome → tts → stt → chat → tts)— 실 사용 흐름에서 매번 4-5 호출.
- 단계 간 일관성 보장 어려움 (welcome 호출 후 chat 호출 사이 시간차에 tts 캐시 만료 등).
- `/welcome` 과 `/chat` 모두 LLM 호출 — 메서드 분리 이득 없음.
- STT 클라 책임 = 클라가 audio 업로드 후 stt 호출 후 chat 호출 → 단계 추적 책임 ↑.

### Option B. 1 endpoint 통합 (send 응답에 사용자 메시지 + AI 텍스트 + AI audio 모두 동기) — 거부

장점:
- 클라 RTT 1 회.
- 응답 시점 모든 데이터 확보.

단점 (거부 이유):
- latency: STT 5s + LLM 35s + TTS 5s ≈ 45s+. UX 손상 큼.
- TTS 비용 낭비 — 사용자가 재생 안 하는 메시지에도 audio 생성.
- 부분 실패 격리 어려움 (TTS 실패가 send 흐름 차단).

### Option C. 본 결정 — 3 endpoint (`/join + /messages + audio lazy GET`) — 채택

장점:
- 클라 RTT 2 (send + audio prefetch — audio 는 사용자 재생 시점 또는 백그라운드 prefetch 가능).
- LLM 호출은 동기 (사용자 명시) — 사용자 메시지 + AI 응답 텍스트 즉시 받음.
- audio = lazy GET → DB 캐시 + CDN 친화 + TTS 비용 최소화.
- 단계 간 일관성 = send 트랜잭션 내 모든 메시지 row commit.

단점:
- send latency ~35s (text) / ~40s (audio 입력) — 클라 로딩 UX 책임.
- audio 첫 재생 시점 추가 ~5s (TTS lazy) — 캐시 후 즉시.

### Option D. AI 응답 비동기 (polling) — 거부

장점:
- send 응답 빠름 (사용자 메시지만).

단점 (사용자 거부):
- polling 주기 의존 — 다음 poll 이 언제일지 모름 (30s 일 수도).
- WebSocket 미사용 환경에서 polling 만으로는 너무 느림.

### Option E. WebSocket / SSE — 거부

사용자 명시: 본 도메인은 실시간 X. 폐기.

### Option F. 방 삭제 — hard-delete cascade (diary 정합) — 거부

사용자 명시: chatroom 은 soft-delete. 메시지 보존 + 감사 / 운영 자연성.

### Option G. leave 의미 재해석 (방 종료) — 거부

사용자 명시: 나간다는 요구사항 자체 없음. DROP.

### Option H. 다중 방 (일기당 N rooms) — 거부

운영 / UX 복잡도 ↑. 1:1 단일 방 채택. 후속 별 PR 시 재검토.

## 결과 및 영향

### 즉시

- **9 PRD §9 채움**:
  - KEEP+FIX: `create / get / join / listParticipants / aiToggle / send / listMessages / poll` (8건)
  - DROP: `leave` (1건)
- **신규 PRD 1건**: [`getMessageAudio.md`](../../prd/diarychat/getMessageAudio.md) (proposed) — 본 결정 박제 cross-reference.
- **`_status.md` diarychat 행**: `9 PRD / 9 mined / 0 proposed` → `10 / 9 / 1`. 합계 endpoint 61 → 62.
- diary-domain-policy 의 "chat-service 구독자 동작 → diarychat 평가 D-a-4 시점 정합" 후속 의무 해소 — soft-delete + 메시지 보존 박제 완료.

### 후속 PR 시리즈 (사용자 "기능 구현 같이" 답 반영)

```
D-a-4-contracts                : chat.proto 갱신 (TranscribeUserAudio 또는 SynthesizeAudio + GenerateChatResponse.audio_url) + catalog 갱신 + ai-service-method-signatures.md 정합
D-a-4-impl-domain              : DiaryChatRoom / ChatMessage / Participant aggregate + Repository ports + 도메인 예외
D-a-4-impl-app                 : 9 Application Service (CreateOrGet / Get / Join / ListParticipants / AiToggle / Send / ListMessages / Poll / GetMessageAudio) + STT/TTS gRPC client port + 히스토리 윈도우 정책
D-a-4-impl-infra               : JpaEntity 3종 + Mapper + Outbox 어댑터 + chat-service gRPC client 어댑터 + Flyway V5 (chatrooms / chat_messages / chat_participants 테이블 + outbox + processed_event)
D-a-4-impl-presentation        : 3 Controller (DiaryChatRoom / ChatMessage / Polling) + DTO + ExceptionHandler + WebMvcTest
D-a-4-status                   : _status.md 단계 행 추가
```

### 결정 대기 (본 결정에서 다루지 않음)

- audio 업로드 endpoint (presigned URL / multipart) — 별 file/upload 도메인 PR.
- audio 만료 / 재생성 정책 (presigned URL TTL) — D-a-4-impl-infra 시점.
- 히스토리 윈도우 (최근 N 메시지) 의 정확한 N — D-a-4-impl-app 시점.
- LLM 컨텍스트 토큰 한도 / 요약 정책 — chat-service 구현 PR 시점.
- 방장 / owner 위임 모델 — 후속 (현 시점 미도입).
- 다른 사용자 차단 — moderation 도메인 후속.
- TTS 캐시 만료 정책 (audioUrl 갱신) — 운영 PR.
- TranscribeUserAudio quota 정확값 — D-a-4-contracts 시점.
- SynthesizeAudio (별 메서드) vs GenerateChatResponse.audio_url 통합 — D-a-4-contracts 시점.

### Non-Goals

- WebSocket / SSE.
- 음성 업로드 endpoint (presigned URL).
- 메시지 삭제 / 수정.
- 알림 / 신고 / moderation.
- 다중 방 (일기당 N rooms).
- leave (사용자 행위).
- 사용자 차단 / 강퇴.
- 방장 / owner 권한 위임.
- audio prefetch 자동화 (lazy GET 만).
- AI 응답 stream (server-streaming RPC) — ADR-0003 후속.

## 참고

- [`prd/diarychat/*.md`](../../prd/diarychat/) §9 (10 PRD)
- [`decisions/diary/diary-domain-policy.md`](diary-domain-policy.md) — Visibility / 404 IDOR / DiaryDeleted Saga
- [`decisions/diary/comment-domain-policy.md`](comment-domain-policy.md) — UUID / 404 통일 / Outbox / boolean 멱등
- [`decisions/diary/validation-ai-fallback-policy.md`](validation-ai-fallback-policy.md) — FAILED 우회 / userId propagation
- [`decisions/contracts/ai-assistant-service-method-catalog.md`](../contracts/ai-assistant-service-method-catalog.md) — `GenerateChatResponse` Deadline 35s, status 카탈로그
- [`decisions/contracts/ai-service-method-signatures.md`](../contracts/ai-service-method-signatures.md) — `SpeechToText` / `TextToSpeech` 4MB unary
- [`docs/architecture/contracts-catalog.md`](../../architecture/contracts-catalog.md) — TranscribeUserAudio / GenerateChatResponse.audio_url 미작성 표기
- [ADR-0003 AI 호출 아키텍처](../../adr/0003-ai-call-architecture.md) — chat-service 게이트웨이 책임 / fallback 원칙
- [ADR-0007 contracts-first 병렬 개발](../../adr/0007-contracts-first-parallel-development.md) — contracts 선행 후 코드 슬라이스
- [CLAUDE.md](../../../CLAUDE.md) — 분산 트랜잭션 금지 / Outbox 의무 / Kafka Consumer 멱등 / 404 IDOR
- Chris Richardson, *Microservices Patterns* (Saga, Outbox)
