# Contracts Catalog

`:contracts` 모듈에 정의된 모든 **gRPC 서비스(proto)** 와 **Kafka 이벤트(Java record)** 의 카탈로그.

> 현재는 모듈/proto/이벤트가 **아직 작성되지 않았으며**, 본 문서는 ADR-0002 결정에 따라 등장할 예정 항목을 사전 카탈로그화한 placeholder. 실제 작성 시점마다 본 문서를 갱신해야 한다 (작성/수정 PR에 본 문서 갱신을 함께 포함).

명명/버전 정책은 ADR-0004(예정) 에서 확정.

---

## 1. gRPC Services (proto)

### 위치 규약
- `contracts/src/main/proto/<service>.proto`
- 패키지: `app.backend.jamo.contracts.proto.<service>`
- 주석으로 **제공자 / 호출자 / 용도** 명시 필수
- field number 변경 금지, 삭제 시 `reserved`

### 서비스 카탈로그

| 서비스 | proto | 제공자 | 호출자 | 용도 | 상태 |
|---|---|---|---|---|---|
| `AiAssistantService` | `chat.proto` | chat-service | diary-service, learning-service(활성화 시), platform-service(미정) | LLM 호출 단일 진입점. `requestSentenceFeedback`, `generateChatResponse`, `validateDiaryContent`, `paraphrase` 등 메서드 보유 예상 | 📝 미작성 |
| `UserSummaryService` | `identity.proto` | identity-service | platform-service(랭킹 표시명) | userId → 닉네임/프로필 사진 등 표시용 요약 조회 | 📝 미작성 |

### 결정 대기 항목 (ADR-0005 예정)
- `AiAssistantService` 의 응답이 unary RPC 인지 server-streaming RPC 인지 (LLM 응답 길이/UX 따라)
- Python LLM 서버와의 proto 공유 방식 (Java/Python 양쪽 빌드)
- gRPC Deadline 표준값 (서비스별 / 메서드별)

---

## 2. Kafka Events

### 위치 규약
- `contracts/src/main/java/app/backend/jamo/contracts/event/<bounded-context>/<EventName>.java`
- Java `record` 로 불변. 순수 JDK 타입만.
- 필수 필드: `eventId`(UUID, 멱등성 키), `occurredAt`(Instant)
- JavaDoc 으로 **발행자 / 구독자 / 토픽 / 용도** 명시 필수
- Breaking Change 시 새 버전 클래스 (`UserWithdrawalRequestedV2`)

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

> 모든 이벤트는 **Outbox 패턴**으로 발행. 구독 측은 **`ProcessedEvent` 멱등성 검증** 필수. 자세한 내용은 `.claude/skills/module-boundary/SKILL.md` (PR #2 갱신 후) 참조.

---

## 3. 갱신 규칙

| 변경 유형 | 액션 |
|---|---|
| 새 proto 파일 추가 | 본 문서 §1 표에 추가, 같은 PR 에서 |
| 새 Kafka 이벤트 추가 | 본 문서 §2 표에 추가, 같은 PR 에서 |
| 필드 추가 (호환) | proto 새 field number / record 새 필드. 본 문서 비고 컬럼에 변경 시점 기록 |
| 필드 제거 | proto `reserved`, record 는 새 버전 클래스. 본 문서에 deprecation 표시 |
| Breaking Change | 새 버전 클래스 (`...V2`). 본 문서에 양쪽 등록 |

---

## 4. 관련 문서

- [ADR-0002 서비스 분할](../adr/0002-service-decomposition.md)
- [Service ↔ Domain Mapping](service-domain-mapping.md)
- (예정) ADR-0004 contracts 명명/버전 표준
- (예정) ADR-0005 AI gateway 인터페이스 설계
