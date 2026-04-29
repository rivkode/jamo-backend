# Decision: sentence-feedback Batch layer 구현 결정

- **상태**: Accepted
- **결정일**: 2026-04-29
- **결정자**: jonghun
- **PR**: D-a-5-impl-batch (`feature/diary-sentence-feedback-impl-batch`)
- **선행 박제**:
  - [`decisions/diary/sentence-feedback-domain-policy.md`](sentence-feedback-domain-policy.md) §3 (TTL 24h batch 전이) / §14 (90일 보존)
  - [`decisions/diary/sentence-feedback-infra-decisions.md`](sentence-feedback-infra-decisions.md) §3 (Outbox poller SKIP LOCKED 패턴) / L3 (ProcessedEvent / Outbox retention 후속 의무)
  - [`decisions/diary/sentence-feedback-presentation-decisions.md`](sentence-feedback-presentation-decisions.md)
- **관련 ADR**: [ADR-0008 Hexagonal 금지 + Lombok 정책](../../adr/0008-hexagonal-and-lombok-policy.md)

## 컨텍스트

D-a-5 시리즈의 **마지막 슬라이스** — sentence-feedback Domain (#62) + Application (#64) + Infrastructure (#71) + Presentation (#73) 위에 두 핵심 batch + 두 retention cleanup 도입:

1. SUGGESTED → EXPIRED 전이 (§3)
2. final 상태 90일 보존 cleanup (§14)
3. ProcessedEvent retention (PR #71 code-reviewer L3)
4. published Outbox retention (PR #71 code-reviewer L3)

## 결정

### 0. 운영 진입 차단 조건 (security-reviewer M2 + code-reviewer H3)

> **본 PR 은 단일 diary-service 인스턴스 가정**. 운영 다중 인스턴스 도입 PR 의 **블로커** 로 다음을 의무 적용:
>
> 1. **ShedLock** (`net.javacrumbs.shedlock-spring`) 또는 Quartz cluster mode — `@Scheduled` 가 N 인스턴스에서 동시 fire 하지 않도록 leader election. 본 PR 의 row-level `FOR UPDATE SKIP LOCKED` 로 race 안전하지만 cron 시각의 동시 진입 자체는 막지 못함.
> 2. **GDPR 보유기간 준수 메트릭** (security-reviewer M3) — `sentence_feedback_backlog_final_older_than_retention` Micrometer Gauge + alert. 02:00 cron 실패 / chunk 부족으로 90일 초과 row 누적 silent fail 회피.
>
> 단일 인스턴스 → 다중 인스턴스 전환 PR 시점에 위 두 항목 동반 도입 없으면 머지 차단.

### 1. 배치 프레임워크 — Spring `@Scheduled`

| 컴포넌트 | 패턴 |
|---|---|
| `SentenceFeedbackExpireBatch` | `@Scheduled(fixedDelayString = "#{@sentenceFeedbackBatchProperties.expireInterval}")` |
| `SentenceFeedbackCleanupBatch` | `@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")` |
| `ProcessedEventCleanupBatch` | `@Scheduled(cron = "0 30 2 * * *", zone = "Asia/Seoul")` |
| `PublishedOutboxCleanupBatch` | `@Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")` |

근거 (OutboxPoller 정합):
- 본 PR 시점 batch 분량 작음 (chunk 100 row × 5분 주기) — Spring Batch / Quartz 의무 도입 비용 대비 가치 적음.
- `@EnableScheduling` 이 이미 활성화 (PR #71 OutboxPoller).
- 단일 인스턴스 가정 + `FOR UPDATE SKIP LOCKED` 로 다중 인스턴스 안전 보강 (OutboxPublisherTx 정합).

거부 옵션:
- (B) Spring Batch — chunk processing / retry / restart 지원, 의존성 + 운영 복잡도 증가. 운영 데이터 양 측정 후 도입 검토 박제.
- (C) Quartz — DB-based distributed scheduling. 의존성 + 별 schema 추가. 다중 인스턴스 leader election 필요 시 도입 박제.

### 2. EXPIRED 이벤트 발행 — 미발행

| 결정 | 근거 |
|---|---|
| `SentenceFeedbackExpired` contracts record **신규 안 함** | 박제 §12 의 3 이벤트 (Requested/Accepted/Rejected) 외 추가 안 함. EXPIRED 는 사용자 결정 없이 만료된 cleanup 의 일부 — 학습 신호 가치 적음. Outbox 트래픽 절약 |

거부 옵션 — 발행: 학습 데이터 (어떤 제안이 결정 안 된 채 EXPIRED 됨) 가치는 있으나 본 시점 분석 파이프라인 부재. 운영 측정 후 재검토 박제.

### 3. 1차 batch — EXPIRED 전이 (§3)

| 항목 | 결정 |
|---|---|
| Application Service | `ExpireSentenceFeedbackService.run(chunkSize)` — find expirable IDs (readOnly TX, SKIP LOCKED) → 각 id 단건 TX `expireOne(id)` (load + Aggregate.expire(clock) + save) |
| Aggregate invariant 보호 | 도메인 `expire(clock)` 가 `clock.instant() >= expiresAt` 검증 (PR #62 ddd-architect Q8) — batch 가 SQL `expires_at < cutoff` 필터링 통과한 row 만 호출, 안전 |
| race condition | `InvalidTransition` (다른 인스턴스 / 사용자 동시 처리) 또는 `NotFound` (cleanup 동시 실행) 발생 시 log.debug + skip + 다른 row 진행 |
| 통계 | `Result(candidates, expired, skipped)` 반환 → batch 가 log.info |

`@Scheduled` 메서드는 try/catch 로 다음 사이클 안전성 보장 (한 사이클 실패가 다음 사이클 차단 X).

### 4. 2차 batch — 90일 보존 cleanup (§14)

| 항목 | 결정 |
|---|---|
| Application Service | `CleanupSentenceFeedbackService.run(retentionDays, chunkSize)` — find final older than (cutoff = now - 90일) → `deleteByIds` (단일 트랜잭션) |
| 회원 탈퇴 즉시 삭제 | 별 경로 — `UserWithdrawalRequestedListener` (PR #71) 가 처리. 본 service 영향 X |
| chunk 단위 | 100 row 1 사이클 → 큰 backlog 시 여러 일에 걸쳐 점진 삭제 (운영 안전). 후속: backlog 가 큰 경우 단일 사이클 chunk 반복 vs cron interval 단축 검토 |

### 5. 3차 batch — ProcessedEvent retention (default 30일)

PR #71 code-reviewer L3 박제 — "장기 운영 시 disk pressure". Kafka 의 max retention (typical 7일) 보다 충분히 길어 재 delivery 가능성 거의 없음. retention 30일은 운영 안전 마진.

### 6. 4차 batch — published Outbox retention (default 7일)

발행 완료 (`published_at IS NOT NULL`) row 만 hard-delete. 발행 실패 / 미발행 row (`published_at IS NULL`) 는 영향 X — 영구 stuck row 모니터링은 별 영역 (PR #71 M-2 박제 후속).

### 7. 단일 사이클 chunk size — 100 row

OutboxPoller / OutboxPublisherTx 정합. 운영 envvar `SENTENCE_FEEDBACK_BATCH_CHUNK_SIZE` 외부화.

### 8. EXPIRED 전이 주기 — default 5분

| 값 | 근거 |
|---|---|
| **5분** | 24h TTL 정확성 (사용자가 24h+5분 후 EXPIRED 확인) vs DB 부하 trade-off. envvar `SENTENCE_FEEDBACK_EXPIRE_INTERVAL` 외부화 |

거부 옵션:
- 1분 — DB 부하 ↑ (낮은 row 갯수 대비 polling 자주). cooldown counter 정확성 vs DB 부하 단순화 우선.
- 1시간 — 사용자가 최대 1시간 늦게 EXPIRED 확인 — UX 저하.

### 9. cleanup cron — 매일 02:00 KST 시리즈

| 시간 | 작업 |
|---|---|
| 02:00 | sentence-feedback 90일 retention cleanup |
| 02:30 | ProcessedEvent 30일 retention cleanup |
| 03:00 | published Outbox 7일 retention cleanup |

오프피크 + 30분 간격 — DB 부하 분산. JVM TZ=Asia/Seoul (docker-compose 이미 설정).

### 10. SKIP LOCKED 적용

EXPIRED 전이 batch — `findExpirableSuggestedIdsForUpdate` (native SQL) 가 MySQL 8 `FOR UPDATE SKIP LOCKED` 사용. 다중 인스턴스 동시 polling 시 같은 row 중복 처리 차단. cleanup batch 도 `findFinalOlderThanIdsForUpdate` 동일 적용 (race 가능성 낮으나 안전 우위).

ProcessedEvent / Outbox cleanup 은 `delete WHERE` JPQL 단순 — chunk 분리 없음 (작업량 적고 race 가능성 낮음 — 두 인스턴스가 같은 row 삭제 시도해도 DB 멱등).

### 10-a. self-invocation 회피 — Tx 별 bean 분리 (code-reviewer C1)

EXPIRED 전이 batch 에서 `run(int)` 가 같은 클래스의 `expireOne(id)` 를 self-invocation 하면 Spring AOP proxy 우회로 `@Transactional` 이 적용 안 됨 → 운영에서 `repository.save(fb)` 시점 `TransactionRequiredException`.

OutboxPublisherTx 패턴 정합 — 별 bean (`ExpireSentenceFeedbackTx`) 으로 트랜잭션 경계 분리:
- `findExpirableIds` (readOnly TX) — native SQL `FOR UPDATE SKIP LOCKED` 가 트랜잭션 안에서 호출되어야 행 잠금 의미 있음 (auto-commit 시 즉시 해제)
- `expireOne` (write TX) — load + Aggregate.expire(clock) + save

`ExpireSentenceFeedbackService.run()` 는 외부 호출 (Batch) 시 proxy 경유로 `tx.findExpirableIds()` / `tx.expireOne()` 호출 → 각각 별 트랜잭션.

### 10-b. `@Modifying` clearAutomatically + flushAutomatically (code-reviewer H2)

bulk delete / update JPQL 5종 모두 `@Modifying(clearAutomatically = true, flushAutomatically = true)` 적용 — 같은 트랜잭션 안에서 stale entity 1차 캐시 잔존 회피.

### 10-c. V6 batch query 인덱스 (security-reviewer M1)

`sentence_feedback (status, decided_at)` + `processed_event (processed_at)` 복합 인덱스 추가. cleanup batch 의 `where status in (...) and decided_at < :cutoff` 가 풀 스캔 + `FOR UPDATE` 로 next-key lock 획득 시 자해성 DoS 회피. `outbox_event` 의 `idx_outbox_event_published_at (published_at, id)` 는 V5 에 이미 있음.

### 11. 도메인 invariant 보호 (Aggregate.expire 호출)

| 옵션 | 결정 |
|---|---|
| **A 채택** | Application Service 가 도메인 `Aggregate.expire(clock)` 호출 — invariant (`clock >= expiresAt`) 보호 |
| 거부 (B) | Batch 가 native UPDATE `set status='EXPIRED' where ...` — 도메인 invariant 우회 |

A 채택 — Aggregate Root 가 라이프사이클 invariant 의 단일 진실 소스.

### 12. 단위 / 슬라이스 테스트 분리

- `ExpireSentenceFeedbackServiceTest` / `CleanupSentenceFeedbackServiceTest` — Mock Repository 단위 (chunk 동작 / race / cutoff)
- `SentenceFeedbackBatchRepositoryDataJpaTest` — Testcontainer MySQL 슬라이스 (native SQL `FOR UPDATE SKIP LOCKED` + status filter + chunk limit + delete)

## 검토한 옵션 (요약)

### A. Spring Batch / Quartz — 거부 (premature)
sentence-feedback batch 분량 작음. 운영 측정 후 도입 검토.

### B. EXPIRED 이벤트 발행 — 거부 (학습 신호 가치 적음)
박제 §12 의 3 이벤트 (사용자 결정 = Requested/Accepted/Rejected) 외 추가 안 함.

### C. cleanup batch 가 native UPDATE 직접 (Aggregate 우회) — 거부
도메인 invariant 단일 진실 소스 보호.

### D. ProcessedEvent / Outbox retention 별 PR — 거부
같은 batch infra 슬라이스 — 단일 PR 효율적.

## 결과 및 영향

### 즉시
- `decisions/_index.md` 1행 추가
- 4 batch 컴포넌트 + 2 Application Service + 1 Properties + Repository port 3 메서드 + native SQL 2 + JPQL delete 3 추가
- 단위 테스트 2 + DataJpaTest 슬라이스 1
- **🎯 sentence-feedback 5 슬라이스 완료** (Domain + App + Infra + Presentation + Batch)

### 후속 PR / 결정 대기

```
(별 PR) Spring Batch / Quartz 도입 — 운영 데이터 양 측정 후 결정
(별 PR) EXPIRED 이벤트 발행 — 학습 분석 파이프라인 도입 시점
(별 PR) 다중 인스턴스 leader election — Quartz / Redis lock
(별 PR) Outbox stuck row 모니터링 — alarm + DLQ (PR #71 M-2)
(별 PR) backlog 큰 cleanup — chunk 반복 또는 cron interval 단축
```

### Non-Goals
- Spring Batch 도입 (분량 작음).
- Quartz 도입 (단일 인스턴스 가정 + SKIP LOCKED 1차 안전).
- EXPIRED 이벤트 발행 (학습 가치 부재).

## 참고

- [`decisions/diary/sentence-feedback-domain-policy.md`](sentence-feedback-domain-policy.md) §3 / §14
- [`decisions/diary/sentence-feedback-infra-decisions.md`](sentence-feedback-infra-decisions.md) §3 / L3
- PR #71 OutboxPoller / OutboxPublisherTx (정합 패턴)
- [CLAUDE.md](../../../CLAUDE.md) — 단일 인스턴스 가정 + SKIP LOCKED 박제
