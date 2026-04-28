# Decision: AiService gRPC 메서드 시그니처 — Complete / SpeechToText / TextToSpeech

- **상태**: Accepted
- **결정일**: 2026-04-28
- **결정자**: jonghun
- **PR**: `feature/contracts-ai-proto`
- **관련 ADR**: [ADR-0003 AI 호출 아키텍처](../../adr/0003-ai-call-architecture.md), [ADR-0004 contracts 명명·버전](../../adr/0004-contracts-naming-and-versioning.md), [ADR-0007 contracts-first 병렬](../../adr/0007-contracts-first-parallel-development.md)
- **관련 카탈로그**: [`contracts-catalog.md`](../../architecture/contracts-catalog.md) §1 / §1.4

## 컨텍스트

ADR-0003 의 후속 결정 항목 4건을 본 결정으로 동시 해소한다:

- [x] `AiService.complete` 시그니처 (input: prompt + temperature + maxTokens + model 등, output: completion + usage + finishReason)
- [x] `AiService.speechToText` 시그니처 (input: audio bytes + format + language, output: text + segments + confidence)
- [x] `AiService.textToSpeech` 시그니처 (input: text + voice + speed, output: audio bytes + format)
- [x] 음성 바이너리 전송: gRPC unary message 크기 제한 (기본 4MB), 큰 파일은 client-streaming 으로 청크 전송 (본 결정에선 4MB unary 채택, 4MB 초과는 후속 ADR)

`AiService` 는 ai-service (Python) 가 노출하고 chat-service (Java) 만 호출하는 순수 AI 추론 게이트웨이 (ADR-0003). 본 결정은 첫 unary 시그니처를 박제하고 server-streaming / client-streaming 은 후속 ADR 로 미룬다.

## 결정

### 1. `Complete` (LLM 텍스트 생성)

**Request** (`CompleteRequest`):
| 필드 | 타입 | field# | 필수 | 비고 |
|---|---|---|---|---|
| `prompt` | `string` | 1 | ✅ | LLM 입력 (이미 chat-service 측에서 템플릿 적용 완료 상태) |
| `temperature` | `double` | 2 | ✅ | 0.0 ~ 2.0. chat-service 가 정책에 따라 결정 후 전달. 0 도 명시적으로 보냄 (proto3 default 0 으로 인한 의미 모호 방지 위해 별도 검증은 ai-service 측에서) |
| `max_tokens` | `int32` | 3 | ✅ | 응답 토큰 상한 |
| `model` | `string` | 4 | optional | 비우면 ai-service 의 기본 모델. 후속 모델 라우팅 활성화 시 채워짐 (예: `gpt-4o-mini`) |
| `request_id` | `string` | 5 | optional | chat-service 가 부여하는 추적 ID (분산 trace / 사용량 카운터 매칭). 없으면 ai-service 가 생성 |

**Response** (`CompleteResponse`):
| 필드 | 타입 | field# | 비고 |
|---|---|---|---|
| `completion` | `string` | 1 | LLM 응답 텍스트 |
| `prompt_tokens` | `int32` | 2 | 입력 토큰 수 (사용량 카운터) |
| `completion_tokens` | `int32` | 3 | 출력 토큰 수 |
| `finish_reason` | `string` | 4 | `stop` / `length` / `error` (proto3 enum 가능했지만 외부 API (OpenAI) 의 reason 문자열 다양성 흡수 위해 string) |
| `model` | `string` | 5 | 실제 사용한 모델 ID (요청과 다를 수 있음 — fallback 시) |
| `request_id` | `string` | 6 | 요청 그대로 echo (없었으면 ai-service 생성값) |

**Deadline**: 30초 (chat-service 측, ADR-0003 §gRPC 운영 정책).

### 2. `SpeechToText` (STT)

**Request** (`SpeechToTextRequest`):
| 필드 | 타입 | field# | 필수 | 비고 |
|---|---|---|---|---|
| `audio` | `bytes` | 1 | ✅ | 음성 바이너리. **첫 단계 unary 4MB 제한 가정** — 1분 wav (~4MB) / 3분 mp3 (~3MB) 까지 안전. 초과 시 client-streaming 도입 (후속). |
| `format` | `string` | 2 | ✅ | `wav` / `mp3` / `m4a` / `webm` |
| `language` | `string` | 3 | optional | BCP-47 (`ko`, `en`, `ja`). 비우면 ai-service 의 자동 언어 감지 |
| `request_id` | `string` | 4 | optional | (Complete 와 동일) |

**Response** (`SpeechToTextResponse`):
| 필드 | 타입 | field# | 비고 |
|---|---|---|---|
| `text` | `string` | 1 | 전사 결과 |
| `confidence` | `double` | 2 | 0.0 ~ 1.0. 모델/공급사가 미제공 시 0 |
| `language` | `string` | 3 | 실제 감지된 언어 (자동 감지 결과 또는 입력 echo) |
| `request_id` | `string` | 4 | (Complete 와 동일) |

**Non-Goals (본 결정 시점)**:
- 화자 분리 (diarization) — `repeated SpeechSegment segments` 추가는 후속 (필드 5 예약).
- 단어별 timestamp — 후속.
- 4MB 초과 client-streaming — 후속 ADR.

**Deadline**: 60초 (ADR-0003 §gRPC 운영 정책 — 음성 길이에 비례).

### 3. `TextToSpeech` (TTS)

**Request** (`TextToSpeechRequest`):
| 필드 | 타입 | field# | 필수 | 비고 |
|---|---|---|---|---|
| `text` | `string` | 1 | ✅ | 음성 합성 입력 |
| `voice` | `string` | 2 | optional | 공급사별 voice ID (`alloy`, `nova`, ... — OpenAI TTS 명명). 비우면 ai-service 기본 |
| `speed` | `double` | 3 | optional | 0.25 ~ 4.0 (OpenAI TTS 범위). 비우면 1.0 |
| `language` | `string` | 4 | optional | 일부 voice 는 다국어 지원 — 명시 권장 |
| `request_id` | `string` | 5 | optional | (Complete 와 동일) |

**Response** (`TextToSpeechResponse`):
| 필드 | 타입 | field# | 비고 |
|---|---|---|---|
| `audio` | `bytes` | 1 | 합성 결과 바이너리 |
| `format` | `string` | 2 | `mp3` / `wav` / `opus` (ai-service 가 결정) |
| `request_id` | `string` | 3 | (Complete 와 동일) |

**Deadline**: 30초 (ADR-0003 §gRPC 운영 정책).

### 4. 시간 / 추적 / 에러 표준

- **시간 필드**: 본 시그니처에 시간 필드 없음. 향후 추가 시 ADR-0004 §3 권고대로 `int64 *_epoch_ms`.
- **request_id 표준**: 모든 메서드에 `request_id` (optional) 포함. chat-service 의 사용량 카운터 / 분산 trace 와 매칭.
- **gRPC 에러 매핑**:
  - 모델 호출 한도 초과 → `Status.RESOURCE_EXHAUSTED`
  - 입력 검증 실패 (audio format 미지원, prompt empty) → `Status.INVALID_ARGUMENT`
  - 외부 API (OpenAI 등) 일시 장애 → `Status.UNAVAILABLE`
  - 모델 timeout → `Status.DEADLINE_EXCEEDED` (gRPC 자체 매핑)
  - 그 외 추론 실패 → `Status.INTERNAL`

## 근거

### 왜 별도 메서드 (3개) 인가
- gRPC 의 메서드 단위 deadline / circuit breaker / metrics 를 자연스럽게 사용 가능.
- LLM (30s) / STT (60s) / TTS (30s) 의 deadline 이 다름.
- 외부 API (OpenAI Chat / Whisper / TTS) 와 1:1 매핑 — 모델 라우팅 변경 시 메서드 단위 격리.
- 단일 메서드 (`call(type, payload)`) 일반화는 type 분기와 payload union 처리로 ai-service 측 코드 복잡도가 증가하고 메서드별 통계 분리 어려움. 이득 대비 손실 큼.

### 왜 `request_id` 를 모든 메서드에?
- chat-service 가 사용량 카운터 / 비용 추적 / rate limit 의 키로 사용.
- 분산 trace (OpenTelemetry) 활성 시 traceId 와 매칭.
- ai-service 가 stateless 이지만 로그 / 메트릭 디버깅 시 chat-service 호출 단위 식별 필수.

### 왜 `string finish_reason` (enum 아님)?
- OpenAI 의 finish_reason 외에도 vLLM, Anthropic 등 미래 공급사의 reason 문자열이 다양 (`stop`, `length`, `tool_calls`, `content_filter`, `error`).
- proto3 enum 으로 고정하면 새 reason 추가 시 contracts breaking change 위험.
- string 으로 두고 호출 측 (chat-service) 이 알려진 값만 분기, unknown 은 fallback.

### 왜 unary?
- ADR-0003 명시: "첫 단계는 unary. server-streaming 은 후속 ADR".
- LLM 긴 응답 / STT 긴 음성 의 streaming 도입은 UX 검증 후 결정.

### 왜 `bytes audio` 직접 (4MB 가정)?
- 첫 단계는 단순. 1분 음성 (mp3 ~1MB) 정도가 PRD 범위 (`chat/transcribeChat`, `chat/speechAudio`).
- 4MB 초과 케이스 (긴 일기 음성 등) 발생 시 후속 ADR 로 client-streaming 도입.
- gRPC 기본 max message size 4MB 는 server / client 양쪽에서 override 가능 — 후속 결정 시 함께.

## 검토한 옵션 (대안)

| 측면 | 채택 | 대안 | 거부 이유 |
|---|---|---|---|
| 메서드 분리 | `Complete` / `SpeechToText` / `TextToSpeech` 3개 | 단일 `call(type, payload)` | 통계/deadline/circuit breaker 메서드 단위 격리 손실, ai-service 측 복잡도 증가 |
| `finish_reason` 타입 | `string` | `enum FinishReason { STOP, LENGTH, ERROR }` | 미래 공급사 reason 다양성 흡수 어려움, contracts breaking change 위험 |
| `temperature` 타입 | `double` | `optional double` (proto3) | proto3 default 0 도 의미 있는 값. ai-service 측 검증으로 충분 |
| 음성 전송 | `bytes` unary | client-streaming chunk | 첫 단계 사용 사례 4MB 이내 충분, streaming 은 후속 ADR |
| 시간 필드 | (현 시점 부재) | `int64 created_at_epoch_ms` 등 | 사용 사례 미발견 — 추가 시 ADR-0004 §3 epoch_ms 표준 적용 |
| 추적 | `request_id` (string, optional) | `metadata` (gRPC native) | metadata 는 분산 trace 도구 등장 시 활용. proto field 는 비즈니스 추적 ID 로 명시 |

## 결과 및 영향

### 즉시
- `contracts/src/main/proto/ai.proto` 가 본 시그니처 그대로 작성됨.
- chat-service 의 `LlmCompletionClient` / `SttClient` / `TtsClient` 도메인 인터페이스 (후속 PR) 가 본 메시지 구조에 맞춰 설계.
- ai-service Python 측 `AiServiceServicer` 구현 (후속 PR) 도 본 시그니처 기반.

### Field number 영구 고정
- 현 시점 모든 필드 번호 (1-6) 영구 사용. 변경 시 새 메서드 / 새 메시지 (ADR-0004 §6).
- `SpeechToTextResponse` 의 field 5 (`segments`) 는 본 결정에서 **예약 (reserved)** 명시 — 후속 화자 분리 도입 시 재할당 방지.
- `CompleteResponse` 의 field 7 이상은 미정 (필요 시 후속).

### 후속 별도 결정 (Open)
- **server-streaming `CompleteStream`** — LLM 긴 응답 UX 검증 후 ADR 박제.
- **client-streaming SpeechToText** — 4MB 초과 음성 사용 사례 등장 시 ADR.
- **모델 카탈로그** — `model` 필드의 실제 허용값 (`gpt-4o-mini`, `whisper-1`, `tts-1` 등) 박제. 운영 비용 / quota 정책과 함께.
- **prompt caching 정책** — Anthropic / OpenAI prompt cache + 자체 캐시 ADR.
- **PII 처리** — 음성 원본 보관 정책 (chat-service 의 `chat.transcribeChat` PRD 와 함께).

### Non-Goals (본 결정 외)
- chat-service 의 `AiAssistantService` (chat.proto) 메서드 카탈로그 — 별도 결정 (PR-B).
- gRPC 인증 (사용자 JWT 전파 여부) — ADR-0003 Open Item.
- mTLS / shared secret — ADR-0003 Open Item.

## 참고

- [ADR-0003 §결과영향 후속결정](../../adr/0003-ai-call-architecture.md)
- [ADR-0004 §3 메시지 명명](../../adr/0004-contracts-naming-and-versioning.md)
- [`contracts-catalog.md`](../../architecture/contracts-catalog.md) §1.4
- [`.claude/skills/module-boundary/SKILL.md`](../../../.claude/skills/module-boundary/SKILL.md) §3.4 ai.proto 예시
- 외부: [OpenAI Chat Completions API](https://platform.openai.com/docs/api-reference/chat), [OpenAI Audio API](https://platform.openai.com/docs/api-reference/audio)
