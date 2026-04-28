# Decision: AiAssistantService 메서드 카탈로그 — 비즈니스 의미별 분리 채택

- **상태**: Accepted
- **결정일**: 2026-04-28
- **결정자**: jonghun
- **PR**: `feature/contracts-chat-proto`
- **관련 ADR**: [ADR-0003 AI 호출 아키텍처](../../adr/0003-ai-call-architecture.md), [ADR-0004 contracts 명명·버전](../../adr/0004-contracts-naming-and-versioning.md), [ADR-0007 contracts-first 병렬](../../adr/0007-contracts-first-parallel-development.md)
- **관련 카탈로그**: [`contracts-catalog.md`](../../architecture/contracts-catalog.md) §1.4 결정 대기

## 컨텍스트

`AiAssistantService` 는 chat-service (Java) 가 노출하는 **AI 비즈니스 게이트웨이** 다. 다른 Java 서비스 (diary / learning / diarychat / validation) 가 호출 ([ADR-0003](../../adr/0003-ai-call-architecture.md)). chat-service 가 프롬프트 템플릿 / 사용량 / rate limit / fallback 정책 처리 후, 내부적으로 ai-service (Python) 의 `AiService` (LLM/STT/TTS) 를 호출.

[`contracts-catalog.md` §1.4 결정 대기 항목](../../architecture/contracts-catalog.md) 의 미해소 항목:

> `AiAssistantService` 메서드 카탈로그 — 비즈니스 의미별 분리 vs 단일 `chatCompletion(type, payload)` 일반화

본 결정은 PR-B (`feature/contracts-chat-proto`, `chat.proto` 첫 정의) 시점에서 이를 박제한다.

## 결정 — 비즈니스 의미별 분리 채택

`AiAssistantService` 는 호출 측 use case 별 명시적 메서드를 가진다. 단일 일반화 메서드 (`call(type, payload)`) 는 채택하지 않는다.

### 초기 메서드 카탈로그 (3개)

본 PR (`chat.proto` 첫 정의) 시점에서 등록되는 메서드는 3개. 각각 현재 PRD 의 use case 와 1:1 매핑.

| 메서드 | 호출자 PRD | 호출자 서비스 | 용도 | Deadline (호출 측) |
|---|---|---|---|---|
| `RequestSentenceFeedback` | [`diary/requestSentenceFeedback.md`](../../prd/diary/requestSentenceFeedback.md) | diary-service | 일기 한 문장 (1~50자) 에 대한 AI 대안 제안 | 35s (chat 자체 + ai 30s 마진) |
| `ValidateDiaryContent` | [`validation/validate.md`](../../prd/validation/validate.md), [`validation/validateLine.md`](../../prd/validation/validateLine.md) | diary-service | 일기 다중 라인 검증 (룰 + LLM 혼합) | 20s |
| `GenerateChatResponse` | [`diarychat/send.md`](../../prd/diarychat/send.md) | diary-service | diarychat 의 AI 어시스턴트 자동 응답 (aiAssistantEnabled=ON 시) | 35s |

### 확장 정책

- 새 use case 등장 시 **새 메서드 추가** (덮어쓰기 / 일반화 변형 X).
- chat 도메인의 14 API 자체 흐름 (예: `chat.transcribeChat`, `chat.speechAudio`) 은 chat-service 의 HTTP endpoint 가 자기 application service → `LlmCompletionClient` / `SttClient` / `TtsClient` (chat-service 도메인 인터페이스) → ai-service 의 `AiService` 를 직접 호출. **`AiAssistantService` 를 경유하지 않음** (자기 자신의 게이트웨이를 자기가 호출하는 모양 회피).
- learning-service 활성화 시 `EvaluateSentence` 등 추가 가능.
- field number / 메서드 이름 정합성은 [ADR-0004 §2-§3](../../adr/0004-contracts-naming-and-versioning.md) 명명 규칙 준수.
- Breaking Change 시 `RequestSentenceFeedbackV2` 등 새 메서드 + 일정 기간 공존 ([ADR-0004 §6](../../adr/0004-contracts-naming-and-versioning.md)).

## 검토한 옵션

### Option A: 단일 일반화 메서드 (`chatCompletion(type, payload)`) — 거부
```protobuf
service AiAssistantService {
  rpc Chat (ChatRequest) returns (ChatResponse);
}
message ChatRequest {
  string type = 1;        // "sentence_feedback" / "validate_diary" / "diarychat_response"
  bytes payload = 2;      // 각 type 별 별도 직렬화
  ...
}
```

**장점**:
- proto 파일이 작음. 새 use case 추가 시 contracts 변경 0.
- chat-service 단일 entry 함수에서 type 분기.

**단점 (거부 이유)**:
- **gRPC 의 메서드 단위 격리 손실** — Deadline / Circuit Breaker / metrics 가 서비스 단위로 묶임. 사용량 카운터도 메서드별 분리 어려움.
- **타입 안전성 손실** — payload 가 bytes 면 schema 검증을 chat-service 의 type 분기 코드에서 직접 해야 함. proto 의 강타입 이점 무력화.
- **호출 측 인터페이스 추적 어려움** — diary-service 에서 "어떤 AI 메서드를 호출하는가" 가 type 문자열로만 표현. IDE / grep 친화적이지 않음.
- **요청/응답 schema 가 union 형태** — proto3 의 `oneof` 로 모델링 가능하지만 단일 메서드 안에 모든 use case 의 union 을 두는 것은 응답 변형 추가 시 contracts breaking change 위험 증가.
- **운영 관측 어려움** — Grafana / Prometheus 의 gRPC 메서드별 latency / error rate 분리가 자연스럽지 않음.

### Option B: 비즈니스 의미별 분리 — 채택
```protobuf
service AiAssistantService {
  rpc RequestSentenceFeedback (SentenceFeedbackRequest) returns (SentenceFeedbackResponse);
  rpc ValidateDiaryContent (ValidateDiaryRequest) returns (ValidateDiaryResponse);
  rpc GenerateChatResponse (ChatResponseRequest) returns (ChatResponseReply);
}
```

**장점 (채택 이유)**:
- gRPC 메서드 단위 Deadline / Circuit Breaker / metrics / Retry 자연 격리.
- 강타입 — IDE 자동완성 / grep 으로 호출 흐름 추적 가능.
- 메서드별 사용량 카운터 / quota / 요금 분리 자연스러움.
- 응답 schema 변형 추가 시 다른 메서드에 영향 0.
- proto 파일이 use case 별로 명시적 — 신규 기여자 onboarding 친화.

**단점**:
- 새 use case 추가 시 contracts 변경 + 빌드 입력. 단 contracts-first 워크플로 ([ADR-0007](../../adr/0007-contracts-first-parallel-development.md)) 와 정합 — 새 AI 사용 use case 는 일반적으로 새 PRD + 새 도메인 모델을 동반.
- proto 파일 크기 증가 — 수용 가능 (3 메서드 → 14 API 시점에도 ~10개 메서드 예상, ai.proto 의 3 메서드 + AiAssistantService 의 ~10 메서드 = contracts 전체 ~13 메서드).

### Option C: 도메인별 sub-service 분리 (`SentenceFeedbackAssistantService`, `ChatAssistantService` 등)
- 1 use case 당 1 service 로 더 세분화.
- **거부 이유**: gRPC 서비스 단위 = chat-service 의 단일 진입점이 자연스러움 (ADR-0003 의 "chat-service 가 AI 비즈니스 게이트웨이" 책임 정의 일치). 너무 세분화하면 chat-service 의 게이트웨이 응집성 약화.

## 근거

### 왜 Option B 가 우선인가
1. **ADR-0003 의 chat-service 책임** — 프롬프트 템플릿 / 사용량 / rate limit 메서드별 정책 — 메서드 단위 격리 필수.
2. **module-boundary 스킬 §3.3 예시** — `RequestSentenceFeedback` / `ValidateDiaryContent` 패턴이 이미 표준 가이드. 본 결정은 그 가이드를 박제.
3. **decisions/contracts/ai-service-method-signatures.md 와 정합** — `AiService` 도 동일 사유 (메서드 단위 격리, 외부 API 1:1 매핑) 로 3 메서드 분리 결정. 일관성.
4. **Option A 의 단점이 너무 큼** — 운영 관측 / 타입 안전성 / 추적성 손실은 contracts 단계에서 회복하기 어려운 부채.

### 왜 chat 도메인 자체 흐름 (transcribe / speechAudio) 은 AiAssistantService 미포함?
- chat-service 의 HTTP 14 API 는 chat-service 자기 도메인 → ai-service (gRPC) 로 직접 호출. 다른 서비스 → chat-service (gRPC) → ai-service 의 흐름이 아님.
- `AiAssistantService` 의 정의는 "**다른 Java 서비스가 chat-service 의 AI 게이트웨이를 호출**". chat-service 자체의 chat 도메인 흐름은 자기 application service 에서 ai-service 직접 호출 (ADR-0003 §gRPC 운영 정책 표 참조).
- 만약 chat 도메인 자체 흐름을 위해 chat-service 가 자기 자신의 `AiAssistantService` 를 호출하면 의미 없는 hop + Deadline 합산 부담 + 중복 책임.

## 결과 및 영향

### 즉시
- `contracts/src/main/proto/chat.proto` 가 본 결정 대로 3 메서드 정의.
- diary-service 의 `SentenceFeedbackOrchestrator` (후속 PR) 가 본 메서드 시그니처 기반으로 chat-service 호출.
- chat-service 의 `AiAssistantGrpcService` (후속 PR) 가 본 메서드 시그니처 구현.

### Field number / 메서드 ID 영구 고정
- 메서드 이름 영구 고정 (gRPC 메서드 ID 는 이름 기반).
- 메시지의 field number 1~ 영구 사용.
- Breaking Change 시 새 메서드 (`RequestSentenceFeedbackV2` 등) + 이전 메서드 deprecation 주석.

### 후속 메서드 후보 (현재 미정의, 등장 PR 시점에 추가)
| 메서드 | 호출자 | 용도 | 등장 시점 |
|---|---|---|---|
| (chat 도메인 자체 흐름) | chat-service 자체 | (자기 application service 가 ai-service 직접 호출) | (해당 메서드 미생성, ai.proto 직접 호출) |
| `EvaluateSentence` (가칭) | learning-service | sentence 학습 평가 | learning-service 활성화 시 |
| `GenerateGreeting` (가칭) | platform-service / 기타 | 마케팅 / 알림용 | use case 등장 시 |

### 각 메서드 status 카탈로그

`status` 필드는 [ai-service-method-signatures.md](ai-service-method-signatures.md) 의 `finish_reason` 과 동일 이유로 string 으로 둔다 (proto3 enum 미사용, 공급사별 신규 값 추가에 호환). 본 결정 시점의 의미 매핑을 박제해 호출 측 (diary-service 등) 의 분기 코드 표준을 제공.

| 메서드 | status 값 | 의미 |
|---|---|---|
| `RequestSentenceFeedback` | `SUGGESTED` | AI 가 1+ 제안을 정상 반환 (suggestions 비어있지 않음). 정상 흐름. |
| `RequestSentenceFeedback` | `FAILED` | AI 호출 실패 / 응답 형식 오류로 fallback 메시지 1개 또는 빈 suggestions 반환. PRD `requestSentenceFeedback.md` §0.1 의 FAILED 상태 트리거. 호출 측은 200 OK + status=FAILED 로 받음 (5xx 매핑 정책은 PRD Open Item). |
| `ValidateDiaryContent` | `VALID` | 모든 라인 검증 통과 (`issues=[]`). |
| `ValidateDiaryContent` | `INVALID` | 1+ 라인이 룰 / LLM 검증 실패 (`issues` 비어있지 않음). 사용자 입력 문제. |
| `ValidateDiaryContent` | `FAILED` | chat-service / ai-service 시스템 오류로 검증 자체 미수행. 호출 측 fallback 권장. |
| `GenerateChatResponse` | `OK` | 정상 응답 생성 (assistant_message 비어있지 않음). |
| `GenerateChatResponse` | `FAILED` | AI 호출 실패. 호출 측 fallback (예: "지금은 답변할 수 없어요"). |
| `GenerateChatResponse` | `RATE_LIMITED` | 사용자별 호출 한도 초과. 호출 측은 사용자에게 한도 안내. gRPC `Status.RESOURCE_EXHAUSTED` 와 별개로 200 + status 응답 (호출 측 UX 단순화 — 5xx 매핑 정책 후속). |

**규칙**:
- 호출 측은 알려진 값만 분기, unknown 은 fallback 으로 처리 (forward 호환).
- 신규 값 추가는 본 결정 로그 갱신 + 카탈로그 갱신 PR 동반.
- gRPC `Status` 코드 (UNAVAILABLE / DEADLINE_EXCEEDED 등) 와 응답 메시지 안의 `status` 필드 (FAILED 등) 의 의미 분리 — 전자는 RPC 자체 실패, 후자는 비즈니스 결과.

### 결정 대기 항목 (본 결정에서 다루지 않음)
- 각 메서드의 정확한 message 필드 / field number — 본 PR 의 `chat.proto` 자체에서 정의 (별도 결정 로그 불필요, proto 헤더 주석 + 본 PR description 으로 박제 충분).
- gRPC 인증 (사용자 JWT 전파 여부) — ADR-0003 Open Item.
- chat-service 의 메서드별 사용량 카운터 / 비용 추적 정책 — ADR-0003 Open Item.
- `tone` 운영 enum 후보 (`casual` / `formal` / ...) — PRD `requestSentenceFeedback.md` §8 Open Question. 해소 시 `SentenceFeedbackRequest` 의 `reserved 6 to 9` 슬롯 활용.
- `validation/validate.md` PRD 가 mined 상태 — 향후 KEEP/FIX/DROP 평가 시 `ValidateDiaryContent` 의 status 카탈로그 / mode 카탈로그 PRD 측 정합 검토.

### Non-Goals
- chat 도메인 14 API 의 chat-service 내부 흐름 (HTTP → application service → ai-service) — 본 결정 범위 외.
- ai-service `AiService` 의 시그니처 — [decisions/contracts/ai-service-method-signatures.md](ai-service-method-signatures.md) 별도.
- server-streaming RPC 도입 — ADR-0003 후속.
- 5xx 매핑 정책 (FAILED status 를 200 응답으로 둘지 / RPC 5xx 로 변환할지) — PRD `requestSentenceFeedback.md` §8 Open Question. chat-service 구현 PR 시 결정.

## 참고

- [ADR-0003 §결과영향 후속결정 — AiAssistantService 메서드 카탈로그](../../adr/0003-ai-call-architecture.md)
- [ADR-0004 §2 gRPC 서비스 / 메서드 명명](../../adr/0004-contracts-naming-and-versioning.md)
- [decisions/contracts/ai-service-method-signatures.md](ai-service-method-signatures.md) — AiService (Python) 시그니처 (동일 패턴 적용)
- [`.claude/skills/module-boundary/SKILL.md`](../../../.claude/skills/module-boundary/SKILL.md) §3.3 chat.proto 예시
- [`docs/architecture/contracts-catalog.md`](../../architecture/contracts-catalog.md) §1
