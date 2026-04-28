# Contracts Catalog

`:contracts` 모듈에 정의된 모든 **gRPC 서비스(proto)** 와 **Kafka 이벤트(Java record)** 의 카탈로그.

> `:contracts` 모듈은 [PR #5](https://github.com/rivkode/jamo-backend/pull/5) 로 골격이 등록됐고, 첫 proto/이벤트는 use case 진행 시점에 PR 별로 추가된다. 본 문서는 작성/수정 PR 마다 함께 갱신해야 한다.

명명/버전/빌드 표준은 [ADR-0004](../adr/0004-contracts-naming-and-versioning.md) 에서 확정.

---

## 1. gRPC Services (proto)

### 위치 규약
- 디렉토리: `contracts/src/main/proto/<service>.proto`
- 패키지: `app.backend.jamo.contracts.proto.<service>`
- 주석으로 **제공자 / 호출자 / 용도 / 언어** 명시 필수
- **양쪽 빌드 입력**: Java (`grpc-spring-boot-starter`) + **Python (`python-services/ai-service` 의 `grpcio-tools`)** ⭐
- field number 변경 금지, 삭제 시 `reserved`

### 서비스 카탈로그

| 서비스 | proto 파일 | 제공자 | 호출자 | 언어 (제공자/호출자) | 용도 | 상태 |
|---|---|---|---|---|---|---|
| `AiAssistantService` | `chat.proto` | chat-service | diary-service (sentence-feedback / validation / diarychat AI), learning-service(활성화 시) | Java / Java | **AI 비즈니스 게이트웨이** — 초기 3 메서드: `RequestSentenceFeedback` / `ValidateDiaryContent` / `GenerateChatResponse`. chat-service 가 프롬프트 템플릿/사용량/rate limit 처리 후 ai-service 호출. chat 도메인 자체 흐름 (transcribe / speechAudio) 은 본 서비스 미경유. (ADR-0003, [decisions/contracts/ai-assistant-service-method-catalog.md](../decisions/contracts/ai-assistant-service-method-catalog.md)) | ✅ 등재 (`chat.proto`) |
| `AiService` ⭐ | `ai.proto` | ai-service (Python) | chat-service (Java) | **Python / Java** | **순수 AI 추론** — `Complete` (LLM) / `SpeechToText` (STT) / `TextToSpeech` (TTS) unary. 모든 메서드에 `request_id` (사용량/trace). 무상태. (ADR-0003, [decisions/contracts/ai-service-method-signatures.md](../decisions/contracts/ai-service-method-signatures.md)) | ✅ 등재 (`ai.proto`) |
| `UserSummaryService` | `identity.proto` | identity-service | platform-service (랭킹 ZSET top N 표시명, [ADR-0002 §결정](../adr/0002-service-decomposition.md)), 사용자 표시명/사진을 실시간으로 조회해야 하는 서비스 | Java / Java | `GetUserSummary` (단건, Deadline 2s) + `BatchGetUserSummaries` (일괄 최대 200, N+1 회피, Deadline 5s). public-safe 필드만 — email / providers / createdAt 제외 ([decisions/identity/user-profile-domain-boundary.md](../decisions/identity/user-profile-domain-boundary.md) `/profiles/me` 본인 조회와 분리). `user_status` 와 RPC `status` 의미 분리 | ✅ 등재 (`identity.proto`) |

### Java↔Python proto 빌드 동기화 (ADR-0003 Open Item)

`contracts/src/main/proto/*.proto` 변경 시 **두 빌드를 모두 갱신**해야 함:

| 측 | 도구 | 산출물 |
|---|---|---|
| Java | `grpc-spring-boot-starter` (Gradle 자동) | `contracts/build/generated/source/proto/main/java/` |
| Python | `python -m grpc_tools.protoc` (수동 또는 Makefile/uv script) | `python-services/ai-service/proto/*_pb2.py`, `*_pb2_grpc.py` |

자동화 방식은 [decisions/contracts/proto-build-sync-makefile.md](../decisions/contracts/proto-build-sync-makefile.md) 에서 **Makefile (`make proto`)** 채택으로 박제 (ADR-0004 §7 권고 일치). CI step (option d) 은 후속 ADR.

### 결정 대기 항목

| 항목 | 상태 | 위치 |
|---|---|---|
| Python 빌드 자동화 방식 | ✅ 결정 (Makefile) | [decisions/contracts/proto-build-sync-makefile.md](../decisions/contracts/proto-build-sync-makefile.md) |
| `AiService.Complete` / `SpeechToText` / `TextToSpeech` 시그니처 | ✅ 결정 | [decisions/contracts/ai-service-method-signatures.md](../decisions/contracts/ai-service-method-signatures.md) |
| gRPC Deadline 표준값 (메서드별) | ✅ 결정 | [ADR-0003 §gRPC 운영 정책](../adr/0003-ai-call-architecture.md) + 위 시그니처 결정 |
| `AiAssistantService` 메서드 카탈로그 (비즈니스 의미별 분리 vs `chatCompletion(type, payload)`) | ✅ 결정 (비즈니스 의미별 분리) | [decisions/contracts/ai-assistant-service-method-catalog.md](../decisions/contracts/ai-assistant-service-method-catalog.md) |
| 응답 RPC 종류: server-streaming (LLM) / client-streaming (긴 음성 STT) 도입 시점 | 📝 후속 ADR | unary 첫 단계 (ADR-0003) |
| 음성 4MB 초과 처리 (client-streaming chunk) | 📝 후속 ADR | (사용 사례 등장 시) |
| 모델 카탈로그 (`gpt-4o-mini` 등 운영 허용값) / quota | 📝 후속 ADR | ADR-0003 후속 |
| ai-service 의 service-to-service 인증 (mTLS / shared secret) | 📝 후속 ADR | ADR-0003 후속 |

---

## 2. Kafka Events

### 위치 규약
- `contracts/src/main/java/app/backend/jamo/contracts/event/<bounded-context>/<EventName>.java`
- Java `record` 로 불변. 순수 JDK 타입만.
- 필수 필드: `eventId`(UUID, 멱등성 키), `occurredAt`(Instant)
- JavaDoc 으로 **발행자 / 구독자 / 토픽 / 용도** 명시 필수
- Breaking Change 시 새 버전 클래스 (`UserWithdrawalRequestedV2`)
- ai-service 는 무상태이므로 Kafka 이벤트 발행/구독 없음 (gRPC 만 사용)

### 이벤트 카탈로그

#### 활동/랭킹 (platform-service event 도메인)

| 이벤트 | 패키지 | 발행자 | 구독자 | 토픽 | 용도 |
|---|---|---|---|---|---|
| `ActivityHappened` | `event/activity/` | diary, chat, comment, learning(활성화 시) | platform | `activity-events` | 사용자 활동 점수 가산 |

#### 회원 탈퇴 Saga (Choreography)

| 이벤트 | 패키지 | 발행자 | 구독자 | 토픽 | 용도 |
|---|---|---|---|---|---|
| `UserWithdrawalRequested` | `event/identity/` | identity | diary, chat, learning, platform | `user-events` | 사용자 데이터 일괄 삭제 트리거 |
| `UserDataPurged` | `event/identity/` | diary, chat, learning, platform | identity | `user-events` | 각 서비스의 삭제 완료 회신 (identity 가 모두 수신 시 User HARD DELETE) |

#### 도메인 이벤트 (diary)

| 이벤트 | 패키지 | 발행자 | 구독자 | 토픽 | 용도 |
|---|---|---|---|---|---|
| `DiaryCreated` | `event/diary/` | diary | platform(랭킹) | `diary-events` | 일기 작성 활동 점수 가산 |
| `DiaryDeleted` | `event/diary/` | diary | platform(랭킹 정정) | `diary-events` | 활동 점수 차감 |
| `CommentCreated` | `event/diary/` | diary | platform(랭킹) | `diary-events` | 댓글 작성 활동 점수 |
| `SentenceFeedbackRequested` | `event/diary/` | diary | platform(랭킹), 학습 분석 | `diary-events` | 문장 피드백 요청 활동 |
| `SentenceFeedbackAccepted` | `event/diary/` | diary | platform(랭킹 가중치), 학습 분석 | `diary-events` | 제안 수락 — 추가 점수 |
| `SentenceFeedbackRejected` | `event/diary/` | diary | (선택) 학습 분석 | `diary-events` | 제안 거부 — 학습 신호 |

#### 도메인 이벤트 (chat)

| 이벤트 | 패키지 | 발행자 | 구독자 | 토픽 | 용도 |
|---|---|---|---|---|---|
| `ChatGenerated` | `event/chat/` | chat | platform(랭킹) | `chat-events` | AI 채팅 생성 활동 |
| `VoiceInputProcessed` | `event/chat/` | chat | platform(랭킹) | `chat-events` | 음성 입력 활동 |

> 모든 이벤트는 **Outbox 패턴**으로 발행. 구독 측은 **`ProcessedEvent` 멱등성 검증** 필수. 자세한 내용은 `.claude/skills/module-boundary/SKILL.md` (PR #2b 갱신 후) 참조.

---

## 3. 갱신 규칙

| 변경 유형 | 액션 |
|---|---|
| 새 proto 파일 추가 | 본 문서 §1 표에 추가, 같은 PR 에서. **Java + Python 양쪽 빌드** 검증 |
| 새 Kafka 이벤트 추가 | 본 문서 §2 표에 추가, 같은 PR 에서 |
| 필드 추가 (호환) | proto 새 field number / record 새 필드. 본 문서 비고 컬럼에 변경 시점 기록 |
| 필드 제거 | proto `reserved`, record 는 새 버전 클래스. 본 문서에 deprecation 표시 |
| Breaking Change | 새 버전 클래스 (`...V2`). 본 문서에 양쪽 등록 |

---

## 4. 관련 문서

- [ADR-0002 서비스 분할](../adr/0002-service-decomposition.md)
- [ADR-0003 AI 호출 분리](../adr/0003-ai-call-architecture.md)
- [ADR-0004 contracts 명명/버전 표준](../adr/0004-contracts-naming-and-versioning.md)
- [ADR-0007 contracts-first 병렬 개발](../adr/0007-contracts-first-parallel-development.md)
- [Service ↔ Domain Mapping](service-domain-mapping.md)
- 결정 로그: [contracts/proto-build-sync-makefile.md](../decisions/contracts/proto-build-sync-makefile.md), [contracts/ai-service-method-signatures.md](../decisions/contracts/ai-service-method-signatures.md), [contracts/ai-assistant-service-method-catalog.md](../decisions/contracts/ai-assistant-service-method-catalog.md)
