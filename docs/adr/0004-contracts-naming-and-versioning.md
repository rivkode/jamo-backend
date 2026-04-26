# ADR-0004: contracts 모듈 명명 / 버전 / 빌드 표준

- **상태**: Accepted
- **결정일**: 2026-04-26
- **결정자**: jonghun
- **관련 ADR**: [ADR-0002 서비스 분할](0002-service-decomposition.md), [ADR-0003 AI 호출 분리](0003-ai-call-architecture.md)

## 컨텍스트

`:contracts` 모듈은 모든 Java 서비스간 + Java↔Python 통신의 **단일 계약 저장소**다. proto 파일, gRPC 서비스, 메서드, 메시지, Kafka 이벤트 record, 패키지명, 토픽명에 일관된 표준이 없으면 다음 문제가 발생:

- 호환성 깨짐 (field number 재사용, 의미 변경)
- 다른 서비스가 호출/구독할 때 명확한 의도 파악 어려움
- Java/Python 빌드 산출물 동기화 실패
- 같은 개념에 여러 이름이 등장 (`User` vs `Account` vs `Customer`)

본 ADR 은 contracts 의 모든 식별자 명명 + 버전 + 빌드 동기화 표준을 정의.

## 검토한 옵션

### Option A: 명시적 표준 정의 (채택)
- **장점**: 일관성 보장, PR 리뷰 시 grep 으로 위반 검출 가능, 후속 자동화 도구 (buf lint) 도입 시 표준이 베이스
- **단점**: 표준 작성/유지 비용

### Option B: 표준 없이 PR 마다 합의
- **장점**: 유연성
- **단점**: 일관성 결여, 같은 개념에 여러 이름 등장, 호환성 위반 사고 위험

### Option C: 외부 도구 (`buf lint`) 즉시 도입
- **장점**: 자동 검증
- **단점**: 학습 비용, 첫 단계엔 over-engineering

## 결정

**Option A 채택.** 본 ADR 이 표준이며, `buf lint` 같은 도구는 추후 별도 ADR (옵션 C 단계적 도입 — Phase 1+ 에서 검토).

### 1. proto 파일 명명

| 항목 | 규칙 | 예시 |
|---|---|---|
| 파일명 | `<service>.proto` (소문자, 단수형) | `chat.proto`, `ai.proto`, `identity.proto` |
| 위치 | `contracts/src/main/proto/<service>.proto` | (위와 같음) |
| proto syntax | proto3 | `syntax = "proto3";` |
| Java/Python 패키지 | `app.backend.jamo.contracts.proto.<service>` | `app.backend.jamo.contracts.proto.chat` |
| Java options | `option java_multiple_files = true;` <br> `option java_package = "...";` <br> `option java_outer_classname = "<Service>Proto";` | `ChatProto`, `AiProto`, `IdentityProto` |

### 2. gRPC 서비스 / 메서드 명명

| 항목 | 규칙 | 예시 |
|---|---|---|
| 서비스명 | PascalCase, `<Domain>Service` 또는 `<Function>Service` | `AiAssistantService` (chat 노출), `AiService` (ai 노출), `UserSummaryService` |
| 메서드명 | PascalCase, **동사**로 시작 | `RequestSentenceFeedback`, `Complete`, `SpeechToText`, `GetUserSummary` |
| 주석 | **제공자 / 호출자 / 용도 / (Python↔Java 시 그 사실)** 명시 필수 | (예시 `module-boundary/SKILL.md` §3.3) |

### 3. proto 메시지 명명

| 항목 | 규칙 | 예시 |
|---|---|---|
| 메시지명 | PascalCase | `SentenceFeedbackRequest`, `CompleteResponse` |
| 필드명 | snake_case | `user_id`, `prompt_tokens`, `expires_at_epoch_ms` |
| field number | 한 번 할당하면 **변경 금지**. 삭제 시 `reserved <num>;` | (호환성 보장) |
| 시간 필드 | `int64 ..._epoch_ms` (밀리초) **권고** | `expires_at_epoch_ms = 5;` |
| Optional | proto3 `optional` 키워드 사용 가능 (단순 기본값 권장) | |

### 4. Kafka 이벤트 record 명명

| 항목 | 규칙 | 예시 |
|---|---|---|
| 파일/클래스명 | PascalCase, **과거형 동사** | `UserWithdrawalRequested`, `DiaryCreated`, `SentenceFeedbackAccepted` |
| 위치 | `contracts/src/main/java/app/backend/jamo/contracts/event/<bounded-context>/<EventName>.java` | `event/identity/`, `event/diary/`, `event/activity/`, `event/chat/` |
| 형식 | Java `record` (불변), 순수 JDK 타입만 | (Spring/Jackson 어노테이션 금지 — ContractsArchitectureTest R2 차단) |
| 필수 필드 | `String eventId` (UUID, 멱등성), `Instant occurredAt` | (검증 생성자 필수) |
| JavaDoc | **발행자 / 구독자 / 토픽 / 용도** 명시 필수 | (예시 `module-boundary/SKILL.md` §3.5) |

### 5. Kafka 토픽 명명

| 토픽 | 용도 |
|---|---|
| `user-events` | 회원 가입 / 탈퇴 / 데이터 정리 (identity ↔ 4 서비스) |
| `activity-events` | 사용자 활동 (diary/chat/comment/learning → platform 랭킹) |
| `diary-events` | diary 도메인 이벤트 (필요 시 분리, 또는 activity-events 통합) |
| `chat-events` | chat 도메인 이벤트 |

> **토픽 분리 정책**: 구독자가 다양한 이벤트를 받아야 하면 통합 (`user-events`), 단일 책임이면 분리. 첫 단계엔 통합 우선, 트래픽 증가 시 분리.

### 6. 버전 정책

#### 호환 변경 (자유)
- proto 새 필드 추가 (새 field number)
- proto 기본값을 갖는 optional 필드 추가
- record 새 필드 추가 (deserializer 가 무시)
- 새 enum value 추가 (구독자가 unknown 처리하면)

#### Breaking Change (새 버전 클래스 필수)
- proto field 의 type 변경
- proto field 의 의미 변경 (호환되더라도)
- record 의 필수 필드 제거 / type 변경
- Kafka 이벤트의 의미 변경

| 변경 유형 | 액션 |
|---|---|
| proto Breaking | 새 service 정의 (`AiServiceV2`) 또는 새 메서드 (`CompleteV2`) |
| record Breaking | 새 클래스 (`UserWithdrawalRequestedV2`) + 이전 버전 deprecation 주석 |

> 이전 버전과 새 버전 모두 **일정 기간(최소 1 release cycle) 공존**. 모든 구독자가 새 버전 처리 가능 확인 후 이전 버전 삭제.

### 7. Java + Python 빌드 동기화

`contracts/src/main/proto/*.proto` 변경 시 양쪽 빌드 모두 갱신:

| 측 | 도구 | 산출물 |
|---|---|---|
| Java | `grpc-spring-boot-starter` (Gradle 자동) | `contracts/build/generated/source/proto/main/java/` |
| Python | `python -m grpc_tools.protoc` (수동 또는 자동화) | `python-services/ai-service/proto/*_pb2.py`, `*_pb2_grpc.py` |

**자동화 방식 후보**:
- (a) Gradle task 가 Python `grpc_tools.protoc` trigger
- (b) **`Makefile` 의 `make proto`** ⭐ **권고 (첫 단계)**
- (c) pre-commit hook
- (d) CI step (PR 빌드 시 양쪽 검증)

**Option (b) 권고** — 가장 단순하고 양쪽 모두 지원. Phase 1+ 에서 (a)/(c)/(d) 로 발전 검토.

```makefile
# 루트 Makefile (예시, 후속 PR 에서 실제 추가)
PROTO_DIR := contracts/src/main/proto
PYTHON_OUT := python-services/ai-service/proto

.PHONY: proto proto-python proto-java

proto: proto-java proto-python

proto-java:
	./gradlew :contracts:generateProto

proto-python:
	cd python-services/ai-service && \
	uv run python -m grpc_tools.protoc \
		--proto_path=../../$(PROTO_DIR) \
		--python_out=proto \
		--grpc_python_out=proto \
		../../$(PROTO_DIR)/ai.proto
```

> Python 측 `proto/*_pb2.py` 와 `*_pb2_grpc.py` 는 자동 생성물이므로 `.gitignore` 등록 권고 (이미 `python-services/ai-service/.gitignore` 에 포함).

### 8. 카탈로그 동기화

contracts 변경 시 다음 문서를 함께 갱신:
- [`docs/architecture/contracts-catalog.md`](../architecture/contracts-catalog.md) — proto 서비스 / Kafka 이벤트 등록 / 발행자 / 구독자 표
- 갱신 안 하면 PR 리뷰에서 차단 (수동 — 향후 ArchUnit 또는 별도 도구로 자동화 검토)

## 결과 및 영향

### 긍정적
- contracts 의 모든 식별자가 일관된 패턴
- proto field number 변경 / 의미 변경 차단
- Java/Python 빌드 동기화 절차 명확
- Breaking Change 시 새 버전 클래스로 안전 전환
- 신규 proto 파일 추가 시 PR 리뷰 체크리스트 명확

### 부정적 / 트레이드오프
- 표준 학습 비용 (PR 리뷰 시 체크리스트)
- proto 빌드 자동화는 첫 단계에 수동 (Makefile) — 변경 시 매번 실행 필요
- `buf lint` 등 자동 검증 도구 미도입 → 표준 위반은 사람 리뷰에 의존
- 토픽 통합 정책은 구독자 늘면 분리 비용 발생

### 후속 결정이 필요한 항목
- **proto 빌드 자동화 방식 확정** — 첫 시점은 (b) Makefile, 후속 (a)/(c)/(d)
- **`buf lint` 등 자동 검증 도구 도입 시점** (Phase 1 또는 2)
- **토픽 분리 정책의 트래픽 한계** (예: 일 100만건 초과 시 분리)
- **시간 필드 표준 강제**: `int64 epoch_ms` vs `google.protobuf.Timestamp` — 권고는 epoch_ms (Java/Python 양쪽 단순)
- **이벤트 schema registry** 도입 여부 (Avro / Confluent SR) — 현재는 record + JSON 직렬화로 출발
- **카탈로그 동기화 자동화** (PR diff 에서 contracts 변경 감지 시 카탈로그 변경 강제)

## 참고
- [ADR-0002 서비스 분할](0002-service-decomposition.md)
- [ADR-0003 AI 호출 분리](0003-ai-call-architecture.md)
- [`docs/architecture/contracts-catalog.md`](../architecture/contracts-catalog.md)
- [`.claude/skills/module-boundary/SKILL.md`](../../.claude/skills/module-boundary/SKILL.md) §3 contracts 모듈 사용법
- 외부: [Protobuf Style Guide](https://protobuf.dev/programming-guides/style/), [gRPC API Design](https://grpc.io/docs/guides/), [Confluent Schema Registry](https://docs.confluent.io/platform/current/schema-registry/index.html)
