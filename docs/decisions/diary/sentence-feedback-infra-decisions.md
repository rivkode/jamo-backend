# Decision: sentence-feedback Infrastructure layer 구현 결정

- **상태**: Accepted
- **결정일**: 2026-04-29
- **결정자**: jonghun
- **PR**: PR D-a-5-impl-infra (`feature/diary-sentence-feedback-impl-infra`)
- **선행 박제**:
  - [`decisions/diary/sentence-feedback-domain-policy.md`](sentence-feedback-domain-policy.md) (16 항목 박제)
  - [`decisions/identity/profile-app-infra-decisions.md`](../identity/profile-app-infra-decisions.md) (참고 패턴 — JpaEntity / Mapper / Flyway)
- **관련 ADR**: [ADR-0003 AI 호출 아키텍처](../../adr/0003-ai-call-architecture.md), [ADR-0005 외래 ID + JPA 연관관계 금지](../../adr/0005-no-jpa-associations.md), [ADR-0007 contracts-first 병렬 개발](../../adr/0007-contracts-first-parallel-development.md), [ADR-0008 Hexagonal 금지 + Lombok 정책](../../adr/0008-hexagonal-and-lombok-policy.md)

## 컨텍스트

`D-a-5-impl-domain` (PR #62) + `D-a-5-impl-app` (PR #64) 위에 infrastructure 어댑터 + DB 영속 + Saga consumer 를 올리는 슬라이스. 다음을 한 번에 도입:

- DB 영속 (sentence_feedback / outbox_event / processed_event)
- chat-service gRPC client (Resilience4j Circuit Breaker / Retry)
- Outbox 패턴 (poller 분리)
- Kafka consumer (DiaryDeleted Saga / UserWithdrawalRequested Saga 회신 발행)

본 결정은 박제 시점에 정해야 했던 9개 항목을 모은다.

## 결정

### 1. suggestions 영속 — JSON 컬럼 (정규화 X)

| 항목 | 결정 |
|---|---|
| 매핑 | Hibernate 6.4+ `@JdbcTypeCode(SqlTypes.JSON)` + MySQL `JSON` 컬럼 |
| 직렬화 단위 | JpaEntity 내부 `SuggestionEmbedded` record (UUID/String/double 4 필드, Jackson 자동 직렬화) |
| 도메인 ↔ Entity 변환 | `SentenceFeedbackMapper` 가 `SuggestionId` wrapper 풀이 (JSON 가독성 단순화) |

근거:
- Suggestion 은 Aggregate 내부 VO — 외부 검색 / 인덱싱 요구 부재.
- 정규화 시 별 테이블 + JOIN 비용 / 트랜잭션 복잡도 증가.
- PRD `requestSentenceFeedback.md §4` 의 데이터 모델 명시 정합 (`suggestions JSON`).

거부 옵션 — 별 테이블 정규화 (`sentence_feedback_suggestion`): 검색 / 단건 인덱싱 요구가 등장하면 본 결정 갱신 + 마이그레이션.

### 2. status / tone — VARCHAR + valueOf 변환 (`@Enumerated` 미사용)

| 컬럼 | 타입 | 변환 |
|---|---|---|
| `status` | VARCHAR(16) NOT NULL | Mapper `Status.valueOf(name)` |
| `tone` | VARCHAR(16) NULL | Mapper `Tone.valueOf(name)` (NULL 보존) |

근거:
- `@Enumerated(EnumType.STRING)` 사용 시 enum 값 추가 / 제거가 schema 변경 없이 silent breaking change 위험.
- 운영 / 수동 SQL 디버깅 편의 (`SELECT status FROM sentence_feedback`).
- Mapper 가 명시적으로 변환 → 새 enum 값 추가 시 컴파일 단계에서 발견.

### 3. Outbox 패턴 — row insert + 별 poller 분리 + 단건 트랜잭션 + SKIP LOCKED

| 컴포넌트 | 책임 |
|---|---|
| `OutboxEventPublisherImpl` | 도메인 트랜잭션 안에서 `outbox_event` row insert만. 4 record 분기 (sentence-feedback 3 + UserDataPurged) |
| `OutboxPoller` | `@Scheduled` (default 5s) — `OutboxPublisherTx.findPendingIds()` 호출 후 단건씩 publish |
| `OutboxPublisherTx` | (1) `findPendingIds()` readOnly 트랜잭션 — `FOR UPDATE SKIP LOCKED` 로 다중 인스턴스 안전 (2) `publishOne(id)` write 트랜잭션 — row 재잠금 → Kafka send (10s timeout) → 명시 UPDATE markPublished |

근거 (code-reviewer C3 / H1 / L3):
- DB transaction 과 Kafka send 의 원자성 분리 — 2PC / JTA 회피 (CLAUDE.md NEVER 정합).
- 단건 트랜잭션 분리 — 한 row 의 send 실패가 다른 row 의 markPublished 를 rollback 시키지 않음.
- 명시 UPDATE (`@Modifying @Query`) — JPA dirty checking 의 트랜잭션 timeout / detached entity 의존 회피.
- `FOR UPDATE SKIP LOCKED` (MySQL 8) — 다중 OutboxPoller 인스턴스 가 같은 row 중복 발행 차단. 단일 인스턴스 환경에서도 무해.
- `kafkaTemplate.send(...).get(10, SECONDS)` — broker 무응답 시 poller 영구 정지 회피.
- send 실패 시 markPublished 미호출 + log.warn (throw 안함) → 다음 폴 사이클 SKIP LOCKED 로 자동 재시도. consumer 측 `ProcessedEvent` 가 멱등 보장.

거부 옵션:
- (a) Direct Kafka send (Outbox 없이): DB commit / Kafka publish 의 원자성 위반 → CLAUDE.md NEVER 위반.
- (b) Spring Modulith / Debezium CDC: 본 시점 도입 비용 / 운영 복잡도 증가. 단일 outbox 패턴이 충분.

### 3-a. Kafka wire format — String payload (Producer/Consumer 일관)

| 항목 | 결정 |
|---|---|
| Producer value-serializer | `StringSerializer` — Outbox row 의 payload (이미 Jackson 직렬화한 raw JSON String) 그대로 발행 |
| Consumer value-deserializer | `StringDeserializer` — Listener 메서드에서 `ObjectMapper.readValue(payload, EventClass.class)` 명시 변환 |
| Type 식별 | 본 PR 시점 listener 별 명시 deserialize (실패 시 다른 listener 가 처리할 메시지로 판단해 ack skip) |

근거 (code-reviewer C1):
- Producer 가 미리 직렬화한 String 을 다시 Spring Kafka `JsonSerializer` 에 넘기면 이중 직렬화. 또는 `JsonDeserializer` 가 `__TypeId__` header 없이 type 추론 불가 → ClassCastException.
- Listener 가 명시 `ObjectMapper.readValue(payload, T.class)` 사용 — type 결정 책임 명시 + sub-domain 별 격리.
- 후속 sub-domain listener 가 같은 토픽에서 다른 type 을 처리할 때, 각 listener 가 자기 type 만 deserialize 시도 → 실패 시 ack skip (다른 listener 가 처리). 단점: 같은 토픽 메시지가 N 개 listener 모두 deserialize 시도 — 운영 스케일 시 type discriminator (Kafka header 또는 payload field) 도입 검토 (후속).

### 3-b. OutboxEventJpaEntity.payload — String + JSON 컬럼 (이중 직렬화 회피)

| 항목 | 결정 |
|---|---|
| Java 타입 | `String` (Jackson 결과) |
| MySQL 컬럼 | `JSON` (`@Column(columnDefinition = "JSON")`) |
| Hibernate 처리 | `@JdbcTypeCode(SqlTypes.JSON)` <b>미적용</b> — String 을 다시 JSON 직렬화 layer 가 처리하면 escape 된 string 으로 저장됨 (code-reviewer M2) |

MySQL JSON 컬럼이 들어오는 String 을 자체 검증/저장 — well-formed JSON 이 아니면 INSERT 실패 (검증 효과).

### 4. Saga consumer — `UserWithdrawalRequested` 구독 / `UserDataPurged` 회신 발행

[`UserWithdrawalRequested.java` JavaDoc](../../../contracts/src/main/java/app/backend/jamo/contracts/event/identity/UserWithdrawalRequested.java) / [`UserDataPurged.java` JavaDoc](../../../contracts/src/main/java/app/backend/jamo/contracts/event/identity/UserDataPurged.java) 정합:

```
1. identity-service: User WITHDRAWING 전이 → UserWithdrawalRequested 발행
2. diary / chat / learning / platform 4 서비스 구독 → 자기 도메인 데이터 일괄 삭제
3. 완료 후 UserDataPurged (sourceService="diary") 회신 발행
4. identity-service 가 모든 회신 수신 시 User HARD DELETE
```

본 PR 의 `UserWithdrawalRequestedListener` 는 sentence_feedback 영역만 cascade. 다른 sub-domain (diary / comment / chatroom / message) 은 후속 슬라이스 (D-a-1 ~ D-a-4 impl) 의 별도 listener (또는 본 listener 확장) 가 처리. 따라서 본 PR 시점 발행하는 `UserDataPurged` 는 sentence-feedback 영역 한정.

> **다른 sub-domain listener 추가 시 결정 필요** — 한 사용자 탈퇴 흐름이 `UserDataPurged` 4 회 발행 (sentence-feedback / diary / comment / chatroom 등) vs 1 회 통합 발행 (모든 sub-domain 완료 대기). identity-service 의 회신 집계 로직 부담 vs Saga 책임 분리 trade-off — 두 번째 sub-domain listener 도입 시 본 결정 갱신.

> **박제 표현 정정 (sentence-feedback-domain-policy.md §14)**: 초기 박제 ("`UserDataPurged` 이벤트 구독")
> 가 contracts 의미와 불일치했음. 정정 — diary-service 는 `UserWithdrawalRequested` 를 구독하고
> `UserDataPurged` 를 회신 발행한다.

### 5. Consumer 멱등성 — `ProcessedEvent` (consumer_id, event_id) 복합 UNIQUE

| 항목 | 결정 |
|---|---|
| 키 | (consumer_id, event_id) 복합 UNIQUE |
| consumer_id 형식 | `diary-service.<sub-domain>.<ListenerClass>` 명시 — 같은 이벤트를 여러 listener 가 다른 의미로 처리 가능 |
| 처리 흐름 | 트랜잭션 안에서 (1) exists 체크 → (2) cascade → (3) ProcessedEvent insert. 부분 실패 시 트랜잭션 rollback → 재시도 안전 |

CLAUDE.md NEVER ("Kafka Consumer 멱등성 미처리 — `ProcessedEvent` 테이블 필수") 정합.

### 6. Resilience4j 정책 — chatService instance

| 항목 | 값 | 근거 |
|---|---|---|
| Circuit Breaker sliding window | 10 (COUNT_BASED) | 연속 실패 빠른 감지 (운영 데이터 기반 후속 조정) |
| Failure rate threshold | 50% | 절반 실패 시 OPEN — 보수적 (chat-service 의 ai-service 재호출 비용 보호) |
| minimumNumberOfCalls | 5 | 부팅 직후 noisy data 보호 |
| OPEN 대기 | 30s | chat-service 가 ai-service 재시작 typical 시간 |
| Half-open permitted calls | 3 | 보수적 회복 검증 |
| recordExceptions | `[io.grpc.StatusRuntimeException]` | gRPC 시스템 오류만 회로 카운트 (code-reviewer H3 — chat-service 의 invariant 위반 IAE 가 OPEN 오작동 트리거하지 않도록) |
| Retry maxAttempts | 3 (시도 총 3회) | gRPC StatusRuntimeException 만 retry — `FAILED` 응답은 retry X (business 의미) |
| Retry waitDuration | 200ms | 빠른 재시도 (Deadline 35s 안에 모든 retry 수렴) |

fallback: `Result.failed("CHAT_UNAVAILABLE")` — sanitized 식별자 (security-reviewer M-4 — exception class / 내부 메시지 영속 회피). Application Service 의 `markFailed` 분기로 일원화.

응답 status 매핑:
- `SUGGESTED` + suggestions 1+ → `Result.suggested(...)` 정상 응답
- `SUGGESTED` + 빈 suggestions → `Result.failed("CHAT_INVALID_RESPONSE: empty suggestions")`
- `SUGGESTED` + Suggestion VO invariant 위반 (UUID / 길이 / confidence) → catch IAE → `Result.failed("CHAT_INVALID_RESPONSE: malformed suggestion")` (code-reviewer N3)
- `FAILED` → `Result.failed("CHAT_FAILED")`
- 기타 status 또는 빈 status → `Result.failed("CHAT_UNKNOWN_STATUS")`

모든 failureReason 은 sanitized 식별자 — chat-service 자유 텍스트 / 내부 메시지가 DB 에 영속되지 않음.

### 7. Flyway V5 통합 (단일 마이그레이션)

| 결정 | 근거 |
|---|---|
| 한 슬라이스 = 한 V5 (3 테이블 통합) | sentence_feedback / outbox_event / processed_event 가 본 슬라이스에서 함께 도입 — 운영 rollback 단위 정합 |

거부 옵션 — V5/V6/V7 분할: 운영 시 부분 rollback 가치 적음. 단일 PR 의 단일 마이그레이션이 직관.

### 8. Kafka cluster — local/dev 부팅 안전 기본값

| 항목 | 결정 |
|---|---|
| 본 PR 시점 docker-compose Kafka container | **추가 안 함** (별 인프라 PR 로 미룸) |
| local / dev profile | `spring.kafka.listener.auto-startup=false` + `missing-topics-fatal=false` (부팅 차단 X) |
| prod profile | `auto-startup=true` (운영 cluster 연결 의무) |
| 단위 / 슬라이스 테스트 | EmbeddedKafkaBroker (Testcontainer Kafka 보다 가벼움) — 본 PR 시점은 listener 단위 테스트만, 통합 테스트는 후속 |

근거:
- 본 PR 의 스코프 제한 (5 서비스 모두 영향 큰 docker-compose Kafka 추가는 별 인프라 PR).
- local 부팅 시 Kafka 미가동 환경에서도 web/data-jpa 흐름 검증 가능 (listener 만 비활성).

후속 인프라 PR (별):
- docker-compose 에 Kafka KRaft mode (Bitnami / Confluent) container 추가
- `.env.example` 에 `KAFKA_BOOTSTRAP_SERVERS` 추가
- 5 Java 서비스 모두 prod profile 로 실 broker 연결 검증

### 9. ArchUnit R6 — Application → Infrastructure 의존 금지

| 결정 | 근거 |
|---|---|
| 본 PR 에서 R6 추가 | infrastructure layer 가 본 PR 에 처음 등장 → 의존 방향 자동 검증 시작점 |

본 검증 통과 = Application Service 가 port 인터페이스 (domain/repository) 만 의존, infrastructure 구현 클래스 직접 import 차단.

## 검토한 옵션 (요약)

### Option A. suggestions 정규화 — 거부

장점: 단건 검색 / partial 갱신 가능.
단점: 외부 검색 요구 부재 / Aggregate 내부 VO → 정규화 가치 적음. JOIN / 트랜잭션 복잡.

### Option B. JSON payload 직접 Kafka send (Outbox 없이) — 거부

CLAUDE.md NEVER 위반 (DB transaction + Kafka 원자성).

### Option C. 다중 sub-domain listener 통합 단일 `UserDataPurged` 발행 — 본 PR 시점 보류

본 PR 은 sentence-feedback 한정. 다른 sub-domain listener 추가 시 결정 필요 (4번 결정 박제).

### Option D. Spring Modulith / Debezium CDC — 거부 (premature)

단일 outbox 패턴이 본 시점 충분. 운영 복잡도 증가.

## 결과 및 영향

### 즉시

- `decisions/diary/sentence-feedback-domain-policy.md §14` 정정 (UserDataPurged 구독 → UserWithdrawalRequested 구독 + UserDataPurged 회신 발행).
- diary-service 가 인프라 layer 첫 도입 — JpaEntity / Mapper / RepositoryImpl / KafkaListener / gRPC client 패턴 정합 박제.
- `_index.md` 갱신 (본 결정 1행 추가).

### 후속 PR 시리즈

```
D-a-5-impl-presentation : DiarySentenceFeedbackController + DTO + ExceptionHandler + WebMvcTest + OpenAPI 의무 충족 (CLAUDE.md "새 서비스 OpenAPI 문서화")
D-a-5-impl-batch        : EXPIRED 전이 배치 잡 + 90일 보존 cleanup 잡 + Quartz / Spring Batch 도입 검토
(별 인프라 PR)            : docker-compose Kafka container + .env.example KAFKA_BOOTSTRAP_SERVERS + 5 서비스 prod profile 통합 검증
```

### 결정 대기 (본 결정에서 다루지 않음)

- 90일 보존 정책의 EXPIRED + 타 final 상태 별도 처리 (D-a-5-impl-batch).
- Outbox poller 의 다중 인스턴스 leader election (`SELECT ... FOR UPDATE SKIP LOCKED` vs Redis lock).
- `UserDataPurged` 통합 발행 정책 (다른 sub-domain listener 추가 시).
- Resilience4j 정책 정확 값 운영 데이터 기반 조정.

### Non-Goals

- Kafka cluster docker-compose 추가 (별 인프라 PR).
- 다중 인스턴스 outbox poller (단일 인스턴스 가정).
- Spring Modulith / Debezium CDC.
- gRPC mTLS / TLS (운영 PR — security-reviewer 영역).

## 참고

- [`decisions/diary/sentence-feedback-domain-policy.md`](sentence-feedback-domain-policy.md)
- [`decisions/identity/profile-app-infra-decisions.md`](../identity/profile-app-infra-decisions.md)
- [ADR-0003 AI 호출 아키텍처](../../adr/0003-ai-call-architecture.md)
- [ADR-0005 외래 ID + JPA 연관관계 금지](../../adr/0005-no-jpa-associations.md)
- [ADR-0008 Hexagonal 금지 + Lombok 정책](../../adr/0008-hexagonal-and-lombok-policy.md)
- [CLAUDE.md](../../../CLAUDE.md) — Outbox 의무 / Kafka Consumer 멱등 / Circuit Breaker / Deadline
- Chris Richardson, *Microservices Patterns* (Saga, Outbox)
