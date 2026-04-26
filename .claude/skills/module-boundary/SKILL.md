---
name: module-boundary
description: MSA 멀티모듈(모노레포) 환경에서 서비스간 경계, 통신, 공유 계약을 다룰 때 사용한다. 새로운 서비스간 통신(gRPC, Kafka 이벤트), contracts 모듈의 proto/이벤트 추가·수정, 서비스간 데이터 일관성(Saga), Circuit Breaker, 멱등성 처리, 모듈 의존성 검증, Redis Read Model 동기화를 포함한다. "다른 서비스 호출", "gRPC", "proto", "이벤트 발행", "Kafka", "Saga", "서비스간", "contracts", "공유 모듈", "캐시 동기화" 키워드가 나오거나 여러 서비스에 영향을 주는 기능을 작업할 때 code-planning 직후 반드시 사용. 서비스 경계 침범을 절대 허용하지 않는다.
---

# Module Boundary — 서비스간 경계와 통신

jamo-backend MSA 멀티모듈(모노레포)에서 **서비스 경계 침범을 방지**하고 **서비스간 통신의 일관성**을 보장하기 위한 스킬.

**기술 스택**: 동기 **gRPC** (Java↔Java + Java↔Python), 비동기 **Kafka**, **MySQL** (Java 서비스별 스키마), **Redis** (조회 캐시 / 활동 랭킹 ZSET / 토큰 블랙리스트)

**서비스 구성**: Java 5 (`identity-service`, `diary-service`, `chat-service`, `learning-service`, `platform-service`) + Python 1 (`ai-service`). 자세한 매핑 → [`docs/architecture/service-domain-mapping.md`](../../../docs/architecture/service-domain-mapping.md), [ADR-0002](../../../docs/adr/0002-service-decomposition.md), [ADR-0003](../../../docs/adr/0003-ai-call-architecture.md)

---

## 1. 절대 원칙

1. **한 Java 서비스 = 하나의 Bounded Context = 하나의 MySQL 스키마**
2. **다른 서비스의 Domain / JpaEntity / Repository 를 import 하지 않는다.**
3. **공유는 오직 `:contracts` 모듈 (proto + Kafka 이벤트) 을 통해서만.**
4. **데이터 중복 허용.** 다른 서비스 데이터가 필요하면 복제하거나 호출하거나. JOIN 금지.
5. **서비스간 호출은 실패를 전제로 설계.** Deadline, Retry, Circuit Breaker 필수.
6. **읽기 캐시와 SoT 를 명확히 구분.** 쓰기는 SoT 서비스에서만.
7. **AI 호출은 chat-service 단일 진입점.** 다른 Java 서비스는 chat-service 의 `AiAssistantService` (gRPC) 만 호출. ai-service 는 chat-service 만 호출. (ADR-0003)

---

## 2. 결정 트리 — 다른 서비스 데이터가 필요할 때

```
필요한 데이터가 다른 서비스에 있는가?
├── 실시간으로 정확한 최신값이 필수인가?
│   ├── 예 → 동기 gRPC 호출 + Deadline + Circuit Breaker
│   │        예: AI 호출 (chat-service 의 AiAssistantService)
│   │        예: 랭킹 표시명 조회 (identity-service UserSummaryService)
│   └── 아니오 → 비동기 이벤트 구독 + 로컬 복제 / Redis Read Model
│                예: platform-service 가 활동 이벤트 구독 → Redis ZSET 갱신
│
└── 내 서비스 내부에서 해결 가능한가? (데이터 복제 / 재설계)
    └── 가능하면 우선 고려. 서비스간 호출 자체를 줄임.
```

**기본 원칙**: **비동기 이벤트 > 동기 gRPC > 피하기** 순으로 선호.

---

## 3. `contracts` 모듈 사용법

### 3.1 구조

```
contracts/
├── build.gradle.kts                # protobuf-gradle-plugin 적용 (Java 측)
└── src/main/
    ├── proto/                      # gRPC 서비스 정의
    │   ├── chat.proto              # AiAssistantService (chat-service Java 노출)
    │   ├── ai.proto                # AiService (ai-service Python 노출, chat-service 만 호출)
    │   └── identity.proto          # UserSummaryService (identity-service Java 노출)
    └── java/app/backend/jamo/contracts/
        └── event/                  # Kafka 이벤트 (Java record)
            ├── activity/
            │   └── ActivityHappened.java
            ├── identity/
            │   ├── UserWithdrawalRequested.java
            │   └── UserDataPurged.java
            ├── diary/
            │   ├── DiaryCreated.java
            │   ├── CommentCreated.java
            │   ├── SentenceFeedbackRequested.java
            │   └── SentenceFeedbackAccepted.java
            └── chat/
                └── ChatGenerated.java
```

> 카탈로그 갱신 → [`docs/architecture/contracts-catalog.md`](../../../docs/architecture/contracts-catalog.md)

### 3.2 절대 두지 않는 것

- ❌ Aggregate, Entity, Value Object (서비스 내부 도메인)
- ❌ JPA Entity
- ❌ 내부 구현 클래스
- ❌ Spring 어노테이션이 붙은 클래스
- ❌ 한 서비스만 쓰는 DTO

### 3.3 Proto — `chat.proto` 예시 (chat-service 의 AI 비즈니스 게이트웨이)

```protobuf
// contracts/src/main/proto/chat.proto
syntax = "proto3";

package app.backend.jamo.contracts.proto.chat;

option java_multiple_files = true;
option java_package = "app.backend.jamo.contracts.proto.chat";
option java_outer_classname = "ChatProto";

// chat-service (Java) 가 제공하는 AI 비즈니스 게이트웨이.
// 호출자: diary-service (sentence-feedback / validation / diarychat AI),
//        learning-service (활성화 시 sentence 학습 평가)
// 내부적으로 chat-service 가 ai-service (Python) 의 AiService 를 호출 (ADR-0003).
service AiAssistantService {
  rpc RequestSentenceFeedback (SentenceFeedbackRequest) returns (SentenceFeedbackResponse);
  rpc ValidateDiaryContent (ValidateDiaryRequest) returns (ValidateDiaryResponse);
  // ... 추가 비즈니스 의미 메서드
}

message SentenceFeedbackRequest {
  string user_id = 1;
  string sentence = 2;        // 1~50자 (PRD)
  repeated string prior_sentences = 3;
  string tone = 4;            // optional
}

message SentenceSuggestion {
  string suggestion_id = 1;
  string text = 2;
  string reason = 3;
  double confidence = 4;
}

message SentenceFeedbackResponse {
  string feedback_id = 1;
  string status = 2;          // SUGGESTED | FAILED
  repeated SentenceSuggestion suggestions = 3;
  repeated string issues = 4;
  int64 expires_at_epoch_ms = 5;
}
```

### 3.4 Proto — `ai.proto` 예시 (ai-service Python 노출, chat-service 만 호출)

```protobuf
// contracts/src/main/proto/ai.proto
syntax = "proto3";

package app.backend.jamo.contracts.proto.ai;

option java_multiple_files = true;
option java_package = "app.backend.jamo.contracts.proto.ai";
option java_outer_classname = "AiProto";

// ai-service (Python) 가 제공하는 순수 AI 추론 게이트웨이.
// 호출자: chat-service (Java) 만. 다른 서비스 / 외부 직접 호출 금지 (ADR-0003).
// LLM + STT + TTS 모두 본 서비스에서 흡수.
service AiService {
  rpc Complete (CompleteRequest) returns (CompleteResponse);
  rpc SpeechToText (SpeechToTextRequest) returns (SpeechToTextResponse);
  rpc TextToSpeech (TextToSpeechRequest) returns (TextToSpeechResponse);
}

message CompleteRequest {
  string prompt = 1;
  double temperature = 2;
  int32 max_tokens = 3;
  string model = 4;           // optional
}

message CompleteResponse {
  string completion = 1;
  int32 prompt_tokens = 2;
  int32 completion_tokens = 3;
  string finish_reason = 4;   // stop | length | error
}

message SpeechToTextRequest {
  bytes audio = 1;
  string format = 2;          // wav / mp3 / m4a
  string language = 3;        // ko / en / ...
}

message SpeechToTextResponse {
  string text = 1;
  double confidence = 2;
}

message TextToSpeechRequest {
  string text = 1;
  string voice = 2;
  double speed = 3;
}

message TextToSpeechResponse {
  bytes audio = 1;
  string format = 2;
}
```

**규칙**:
- 파일명: `<service>.proto`
- 패키지는 `app.backend.jamo.contracts.proto.<service>`
- 주석으로 **제공자 / 호출자 / 용도 / (Python ↔ Java 시 그 사실)** 명시
- field number 는 **한 번 할당하면 변경 금지** (wire 호환성)
- 삭제 필드는 `reserved` 로 영구 차단
- **`ai.proto` 는 Java + Python 양쪽 빌드 입력**: Python 측은 `python -m grpc_tools.protoc` 로 `python-services/ai-service/proto/` 에 생성

### 3.5 Kafka 이벤트

```java
// contracts/src/main/java/app/backend/jamo/contracts/event/identity/UserWithdrawalRequested.java
package app.backend.jamo.contracts.event.identity;

import java.time.Instant;

/**
 * 사용자가 회원 탈퇴를 요청했을 때 identity-service 가 발행하는 이벤트.
 *
 * <p>발행자: identity-service
 * <p>구독자: diary-service, chat-service, learning-service, platform-service
 *           (각자 사용자 데이터 삭제 후 {@code UserDataPurged} 회신)
 * <p>토픽: {@code user-events}
 */
public record UserWithdrawalRequested(
    String eventId,              // 멱등성 키 (UUID)
    Instant occurredAt,
    String userId
) {
    public UserWithdrawalRequested {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
    }
}
```

**규칙**:
- `record` 로 불변
- `eventId`, `occurredAt` 필수
- 순수 JDK 타입만 (Spring/Jackson 어노테이션 금지)
- JavaDoc 으로 발행자 / 구독자 / 토픽 / 용도 명시
- Breaking Change 시 버전 분리 (`UserWithdrawalRequestedV2`)

---

## 4. 비동기 이벤트 (Kafka)

### 4.1 발행 측 (Producer) — Outbox 패턴

DB 트랜잭션과 이벤트 발행의 원자성 보장.

```java
// <service>/.../infrastructure/messaging/OutboxEventPublisher.java
@Component
public class OutboxEventPublisher {

    private final OutboxJpaRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public void publish(Object event, String topic) {
        try {
            OutboxEntryJpaEntity entry = new OutboxEntryJpaEntity(
                UUID.randomUUID().toString(),
                topic,
                event.getClass().getName(),
                objectMapper.writeValueAsString(event),
                Instant.now()
            );
            outboxRepo.save(entry);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException(e);
        }
    }
}

// 별도 스케줄러(@Scheduled)가 Outbox → Kafka 로 전달 (at-least-once)
```

**MySQL Outbox 테이블** (Flyway):
```sql
CREATE TABLE outbox_entries (
    id           VARCHAR(36) PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    event_type   VARCHAR(255) NOT NULL,
    payload      JSON NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    published_at DATETIME(6),
    INDEX idx_outbox_unpublished (published_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

각 Java 서비스의 자기 스키마(`identity` / `diary` / `chat` / `platform` / `learning`) 안에 `outbox_entries` 테이블 보유.

### 4.2 구독 측 (Consumer) — 멱등성 필수

```java
// platform-service/.../infrastructure/messaging/ActivityHappenedListener.java
import app.backend.jamo.contracts.event.activity.ActivityHappened;

@Component
public class ActivityHappenedListener {

    private final ProcessedEventRepository processedEventRepo;
    private final RankingZSetUpdater rankingUpdater;

    @KafkaListener(topics = "activity-events", groupId = "platform-service")
    @Transactional
    public void on(ActivityHappened event) {
        // 1. 멱등성 체크 (MySQL processed_events 테이블)
        if (processedEventRepo.existsByEventId(event.eventId())) {
            return;
        }

        // 2. 비즈니스 처리 — Redis ZSET 점수 가산
        rankingUpdater.incrementScore(event.userId(), event.points());

        // 3. 처리 완료 기록
        processedEventRepo.save(new ProcessedEvent(event.eventId(), Instant.now()));
    }
}
```

**규칙**:
- `contracts` 에 정의된 타입만 import
- 처리 실패 시 DLQ 로 이동
- 절대 서비스 내부 Domain 타입으로 바로 받지 않음
- 멱등성 저장소는 **같은 트랜잭션의 MySQL 테이블** 사용

---

## 5. 동기 gRPC 호출

### 5.1 Java↔Java — chat-service 가 `AiAssistantService` 제공 (서버)

```java
// chat-service/.../infrastructure/grpc/server/AiAssistantGrpcService.java
import app.backend.jamo.contracts.proto.chat.*;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class AiAssistantGrpcService extends AiAssistantServiceGrpc.AiAssistantServiceImplBase {

    private final SentenceFeedbackOrchestrator orchestrator;

    @Override
    public void requestSentenceFeedback(SentenceFeedbackRequest request,
                                         StreamObserver<SentenceFeedbackResponse> responseObserver) {
        try {
            // chat-service 의 비즈니스 로직: 프롬프트 / rate limit / fallback
            SentenceFeedbackResult result = orchestrator.process(
                new UserId(request.getUserId()),
                request.getSentence(),
                request.getPriorSentencesList());

            responseObserver.onNext(toProto(result));
            responseObserver.onCompleted();
        } catch (RateLimitExceededException e) {
            responseObserver.onError(Status.RESOURCE_EXHAUSTED
                .withDescription(e.getMessage()).asRuntimeException());
        } catch (AiUnavailableException e) {
            responseObserver.onError(Status.UNAVAILABLE
                .withDescription("AI 일시 장애").asRuntimeException());
        }
    }
}
```

**규칙**:
- gRPC 서비스 클래스는 **`infrastructure/grpc/server/`** 에만
- Application Service 호출. Repository 직접 호출 금지.
- Domain 결과 → proto 응답으로 변환 (Mapper)
- 예외 → 적절한 gRPC `Status` 매핑 (NOT_FOUND, INVALID_ARGUMENT, RESOURCE_EXHAUSTED, UNAVAILABLE 등)

### 5.2 Java↔Java — diary-service 가 chat-service 호출 (클라이언트)

Application Service 가 **직접** gRPC stub 을 쓰지 않는다. Domain 에 인터페이스, Infrastructure 에서 gRPC 로 구현.

```java
// diary-service/.../domain/sentencefeedback/AiAssistantClient.java  (Domain 인터페이스)
public interface AiAssistantClient {
    SentenceFeedbackResult requestSentenceFeedback(
        UserId userId, String sentence, List<String> priorSentences);
}
```

```java
// diary-service/.../infrastructure/grpc/client/GrpcAiAssistantClient.java
import app.backend.jamo.contracts.proto.chat.*;
import net.devh.boot.grpc.client.inject.GrpcClient;

@Component
public class GrpcAiAssistantClient implements AiAssistantClient {

    @GrpcClient("chat-service")
    private AiAssistantServiceGrpc.AiAssistantServiceBlockingStub stub;

    @Override
    @CircuitBreaker(name = "chat-service", fallbackMethod = "fallback")
    @Retry(name = "chat-service")
    public SentenceFeedbackResult requestSentenceFeedback(
            UserId userId, String sentence, List<String> priorSentences) {
        try {
            SentenceFeedbackResponse response = stub
                .withDeadlineAfter(35, TimeUnit.SECONDS)  // chat 자체 + ai 30s 마진 (ADR-0003)
                .requestSentenceFeedback(SentenceFeedbackRequest.newBuilder()
                    .setUserId(userId.value())
                    .setSentence(sentence)
                    .addAllPriorSentences(priorSentences)
                    .build());

            return SentenceFeedbackMapper.toDomain(response);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
                throw new AiUnavailableException(e);
            }
            throw new ChatServiceException(userId, e);
        }
    }

    private SentenceFeedbackResult fallback(UserId userId, String sentence,
                                             List<String> priorSentences, Throwable t) {
        // Circuit Open 시 정형 fallback
        return SentenceFeedbackResult.failed("지금은 제안을 받을 수 없어요");
    }
}
```

**규칙**:
- `@GrpcClient` 주입은 **`infrastructure/grpc/client/`** 에만
- **Deadline 필수** — 표준값은 ADR-0003 의 운영 정책 표 참조
- Resilience4j `@CircuitBreaker` + `@Retry` 필수
- contracts proto 응답 → Domain 객체 변환 (Mapper)
- `StatusRuntimeException` 을 도메인 예외로 변환
- Fallback 은 **반드시 정의** (사용자 UX 보호)

### 5.3 Java↔Python — chat-service 가 ai-service 호출 (ADR-0003 핵심)

```java
// chat-service/.../domain/ai/LlmCompletionClient.java (Domain 인터페이스)
public interface LlmCompletionClient {
    LlmCompletion complete(String prompt, double temperature, int maxTokens);
}
```

```java
// chat-service/.../infrastructure/grpc/client/GrpcAiServiceClient.java
import app.backend.jamo.contracts.proto.ai.*;

@Component
public class GrpcAiServiceClient implements LlmCompletionClient {

    @GrpcClient("ai-service")
    private AiServiceGrpc.AiServiceBlockingStub stub;

    @Override
    @CircuitBreaker(name = "ai-service-llm", fallbackMethod = "completeFallback")
    @Retry(name = "ai-service-llm")
    public LlmCompletion complete(String prompt, double temperature, int maxTokens) {
        try {
            CompleteResponse response = stub
                .withDeadlineAfter(30, TimeUnit.SECONDS)  // LLM 추론 (ADR-0003)
                .complete(CompleteRequest.newBuilder()
                    .setPrompt(prompt)
                    .setTemperature(temperature)
                    .setMaxTokens(maxTokens)
                    .build());

            return new LlmCompletion(
                response.getCompletion(),
                response.getPromptTokens(),
                response.getCompletionTokens(),
                response.getFinishReason());
        } catch (StatusRuntimeException e) {
            throw new AiServiceException(e);
        }
    }

    private LlmCompletion completeFallback(String prompt, double temperature,
                                            int maxTokens, Throwable t) {
        throw new AiServiceUnavailableException(t);
    }
}
```

**Python 측 (참고만 — Python 코드는 본 스킬 대상 외)**:

```python
# python-services/ai-service/grpc_server.py
from proto import ai_pb2, ai_pb2_grpc
import grpc

class AiServiceImpl(ai_pb2_grpc.AiServiceServicer):
    def Complete(self, request, context):
        # OpenAI / vLLM 등 호출
        completion = self.llm_client.complete(
            prompt=request.prompt,
            temperature=request.temperature,
            max_tokens=request.max_tokens,
        )
        return ai_pb2.CompleteResponse(
            completion=completion.text,
            prompt_tokens=completion.prompt_tokens,
            completion_tokens=completion.completion_tokens,
            finish_reason=completion.finish_reason,
        )
```

**규칙 (Java↔Python gRPC 추가 사항)**:
- `ai.proto` 의 Python 측 빌드 산출물(`*_pb2.py`, `*_pb2_grpc.py`)이 `python-services/ai-service/proto/` 에 있어야 함 — 빌드 자동화는 ADR-0003 Open Item
- chat-service 외 다른 서비스가 `AiServiceGrpc` stub 을 import 하면 Critical (ADR-0003 위반)
- ai-service 까지 사용자 JWT 전파 여부는 ADR-0003 Open Item — 기본은 chat-service 에서 차단하는 보수적 시작
- 응답 RPC: 첫 단계는 unary (ADR-0003). server-streaming 은 후속 ADR

### 5.4 gRPC 설정 예시

```yaml
# diary-service/src/main/resources/application.yml
grpc:
  client:
    chat-service:
      address: "static://${services.chat.host:chat-service}:${services.chat.grpc-port:9090}"
      negotiation-type: plaintext
      enable-keep-alive: true
      keep-alive-time: 30s

# chat-service/src/main/resources/application.yml
grpc:
  client:
    ai-service:
      address: "static://${services.ai.host:ai-service}:${services.ai.grpc-port:9090}"
      negotiation-type: plaintext

resilience4j:
  circuitbreaker:
    instances:
      chat-service:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
      ai-service-llm:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
  retry:
    instances:
      chat-service:
        max-attempts: 1               # 다른 서비스 → chat 은 Retry 0 (ADR-0003)
      ai-service-llm:
        max-attempts: 2               # chat → ai 는 1회 재시도
        wait-duration: 500ms
```

---

## 6. 데이터 일관성 — Saga 패턴

여러 서비스에 걸친 트랜잭션은 분산 트랜잭션 대신 Saga 로 처리.

**대표 케이스: 회원 탈퇴 (Choreography)**

1. **identity-service**: User 상태 = WITHDRAWING + `UserWithdrawalRequested` 이벤트 발행 (Outbox)
2. **diary-service**: 사용자의 diary / comment / sentence-feedback / diarychat 데이터 삭제 → `UserDataPurged.diary` 회신
3. **chat-service**: 사용자의 chat 메시지 / 채팅방 / 사용량 카운터 삭제 → `UserDataPurged.chat` 회신
4. **learning-service**: sentence / word 삭제 → `UserDataPurged.learning` 회신 (활성화 시)
5. **platform-service**: shorts / event / feedback / Redis ZSET 점수 삭제 → `UserDataPurged.platform` 회신
6. **identity-service**: 모든 회신 수신 시 User HARD DELETE
7. **보상**: 일정 시간 내 미회신 → 운영 알림 + 수동 정리

> ai-service 는 무상태이므로 Saga 참여 X.

자세한 시나리오 / 보상 / 멱등성 → [`references/saga-example.md`](references/saga-example.md)

---

## 7. Read Model 동기화 — platform-service 활동 랭킹 ZSET

### 7.1 구조

```
[diary / chat / comment / learning] (각자 SoT)
  자기 도메인 활동 발생 (DiaryCreated, ChatGenerated, CommentCreated 등)
  → ActivityHappened{userId, type, points, occurredAt, eventId} 이벤트 Outbox 발행
         ↓ Kafka activity-events
[platform-service] (Read Model)
  ActivityListener 가 구독 → ProcessedEvent 멱등성 → 점수 정책 적용
  → Redis ZSET ranking:global ZINCRBY {points} {userId}
  랭킹 조회 API: ZREVRANGE ranking:global 0 99 WITHSCORES
  사용자 표시명: identity-service UserSummaryService gRPC 호출 (또는 Redis 캐시)
```

### 7.2 핵심 규칙

- **이름을 다르게 한다**: 쓰기 SoT 는 각 서비스의 도메인(예: `Diary` Aggregate), 읽기 Read Model 은 단순 Redis 키 (`ranking:global`). 같은 이름 금지.
- **Read Model 은 Aggregate 가 아니다**: JPA Entity 아님, 불변식 검증 없음, Redis key-value / ZSET.
- **stale 허용**: 랭킹 응답에 "최대 수 초 지연" 명시 가능. 점수 재계산은 활동 발생 서비스에서.
- **캐시 재구축**: Kafka 이벤트 retention 안에서 재처리 가능. 이벤트 유실 대비 각 서비스의 활동 데이터 SoT 재계산 배치 필수.
- **TTL 과 갱신 정책 명시**: 글로벌 랭킹 영구, 주간/월간 랭킹 키별 TTL (`ranking:weekly:{yyyyww}` 등). ADR-0002 후속 결정.

자세한 ZSET 운영 / 재구축 / 일/주/월 키 → [`references/read-model-sync.md`](references/read-model-sync.md)

---

## 8. 모듈 의존성 검증

### 8.1 `build.gradle.kts` 규칙

```kotlin
// diary-service/build.gradle.kts
dependencies {
    implementation(project(":contracts"))                // ✅
    implementation(project(":common-auth-jwt"))          // ✅
    implementation(project(":common-infrastructure"))    // ✅

    // implementation(project(":chat-service"))          // ❌
    // implementation(project(":identity-service"))      // ❌

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    runtimeOnly("com.mysql:mysql-connector-j")

    implementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")
    implementation("net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("io.github.resilience4j:resilience4j-spring-boot3")
}
```

`python-services/ai-service/` 는 Gradle 외부이므로 `settings.gradle.kts` 의 `include(...)` 대상에 포함되지 않는다.

### 8.2 ArchUnit 자동 검증

자세한 규칙 → [`references/archunit-rules.md`](references/archunit-rules.md)

---

## 9. 자가 검증 체크리스트

### 이벤트 발행
- [ ] `contracts/event/` 에 스키마 정의?
- [ ] `eventId`, `occurredAt` 필드?
- [ ] 발행자/구독자/토픽/용도 JavaDoc?
- [ ] Outbox 패턴 사용?

### 이벤트 구독
- [ ] 멱등성 처리(`ProcessedEvent`)?
- [ ] contracts 타입만 받고 Domain 변환?
- [ ] 실패 시 DLQ 이동?

### gRPC 호출 (Java↔Java + Java↔Python 공통)
- [ ] Domain 인터페이스로 래핑?
- [ ] `withDeadlineAfter(...)` 설정 (ADR-0003 표준값 준수)?
- [ ] Circuit Breaker + Retry?
- [ ] Fallback 정의?
- [ ] proto → Domain 변환?
- [ ] `StatusRuntimeException` → 도메인 예외 매핑?

### gRPC 서비스 제공
- [ ] `@GrpcService` 가 `infrastructure/grpc/server/` 에?
- [ ] Application Service 경유?
- [ ] Domain 예외 → gRPC `Status` 매핑?

### AI 호출 (ADR-0003 추가)
- [ ] 다른 Java 서비스가 ai-service `AiServiceGrpc` 를 직접 import 하지 않는가? (chat-service 만 허용)
- [ ] chat-service 외 다른 서비스가 ai-service 를 호출하지 않는가?
- [ ] chat-service → ai-service 호출의 Deadline 이 메서드별로 적절한가? (LLM 30s / STT 60s / TTS 30s)

### Read Model
- [ ] 쓰기 Aggregate 와 Read Model 이름이 명확히 구분?
- [ ] 캐시 재구축 배치?
- [ ] stale 정책 명시?
- [ ] SoT 재검증?

### Saga
- [ ] 각 단계 보상 트랜잭션 또는 미회신 처리?
- [ ] 실패 시나리오 최종 상태?
- [ ] ADR 문서화?

### 의존성
- [ ] 다른 Java 서비스 모듈 참조 없음?
- [ ] ArchUnit 통과?
- [ ] `python-services/ai-service` 가 `settings.gradle.kts` 에 포함되지 않음?

---

## 10. 안티패턴 (즉시 차단)

| 안티패턴 | 올바른 방법 |
|---|---|
| 다른 서비스 MySQL 에 직접 쿼리 | gRPC 호출 또는 이벤트 구독으로 복제 |
| `contracts` 에 Aggregate / JpaEntity 둠 | 서비스 내부 유지. contracts 는 proto/이벤트만 |
| gRPC stub 을 Application Service 에서 직접 호출 | Domain 인터페이스로 래핑 |
| gRPC 호출에 Deadline 미설정 | **반드시** `withDeadlineAfter(...)` |
| Kafka Consumer 에 멱등성 없음 | `ProcessedEvent` 테이블 |
| 서비스 모듈간 `implementation(project(...))` 참조 | 금지. contracts / common-* 만 |
| proto 필드 번호 재사용 / 의미 변경 | `reserved` 로 영구 차단 |
| 쓰기 Aggregate 와 Read Model 이름 동일 | 명확히 구분 (예: `Diary` Aggregate vs Redis `ranking:global`) |
| Redis 캐시만 믿고 비즈니스 결정 | 반드시 SoT 에서 재검증 |
| 분산 트랜잭션 (2PC, JTA) 시도 | Saga + 보상 트랜잭션 |
| ai-service 를 chat-service 외에서 호출 | chat-service 의 `AiAssistantService` 경유 (ADR-0003) |
| chat-service 가 OpenAI / Whisper 등 직접 호출 | ai-service 의 `AiService.complete/speechToText/textToSpeech` 위임 |
| Python 서버 응답을 Java 측 그대로 노출 | proto → Domain 변환 필수 |

---

## 다음 단계

→ `.claude/skills/ddd-architecture/SKILL.md` (서비스 내부 구현)
