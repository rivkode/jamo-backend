# Saga 예시 — 회원 탈퇴 (Choreography)

`module-boundary/SKILL.md` §6 의 상세 시나리오.

여러 서비스에 걸친 트랜잭션은 분산 트랜잭션(2PC, JTA) 대신 **Saga + 보상 트랜잭션**으로 처리한다. jamo 의 대표 케이스는 **회원 탈퇴** — 5개 Java 서비스에 흩어진 사용자 데이터를 모두 정리한 뒤 identity-service 가 최종적으로 User 를 HARD DELETE 한다.

> ai-service 는 무상태이므로 Saga 참여 X.

---

## 1. 흐름 (Choreography)

```
[SPA] ── HTTPS DELETE /api/v1/users/me ──▶ [identity-service]
                                                    │ 1. User.requestWithdrawal() — 상태 WITHDRAWING
                                                    │ 2. UserWithdrawalRequested 이벤트 Outbox 발행
                                                    │    (DB tx 안에서 outbox_entries 에 저장)
                                                    │
                                                    │ scheduler → Kafka(user-events)
                                                    ▼
            ┌──────────────────────┬──────────────────────┬──────────────────────┐
            │                      │                      │                      │
            ▼                      ▼                      ▼                      ▼
    [diary-service]         [chat-service]         [learning-service]    [platform-service]
     ProcessedEvent 체크     ProcessedEvent 체크     ProcessedEvent 체크   ProcessedEvent 체크
     사용자 데이터 삭제        사용자 데이터 삭제        sentence/word 삭제     shorts/event/feedback
     (diary, comment,         (chat, channel,                                + Redis ZSET 점수
      sentence_feedback,       usage counter)                                  ZREM ranking:global
      diarychat 등)
            │                      │                      │                      │
            │ UserDataPurged.diary │ UserDataPurged.chat  │ ...                  │ UserDataPurged.platform
            ▼                      ▼                      ▼                      ▼
                            Kafka(user-events)
                                    │
                                    ▼
                          [identity-service]
                            UserDataPurgedListener
                            ProcessedEvent 체크
                            WithdrawalProgress 갱신 (4개 서비스 회신 모두 받았는지)
                            모두 OK → User HARD DELETE + UserDeleted 발행 (선택)
```

---

## 2. 이벤트 정의

```java
// contracts/event/identity/UserWithdrawalRequested.java
public record UserWithdrawalRequested(
    String eventId, Instant occurredAt, String userId
) { /* 검증 생략 */ }

// contracts/event/identity/UserDataPurged.java
public record UserDataPurged(
    String eventId, Instant occurredAt,
    String userId,
    String service          // "diary" | "chat" | "learning" | "platform"
) { /* 검증 생략 */ }
```

토픽: `user-events` (모든 회원 탈퇴 관련 이벤트 단일 토픽). 구분은 이벤트 타입 + `service` 필드.

---

## 3. 발행 측 — identity-service

```java
// identity-service/.../application/UserWithdrawalService.java
@Service
public class UserWithdrawalService {

    private final UserRepository userRepo;
    private final OutboxEventPublisher outbox;
    private final Clock clock;

    @Transactional
    public void requestWithdrawal(UserId userId) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        user.requestWithdrawal(Instant.now(clock));   // Domain 메서드 — 상태 WITHDRAWING 으로 전이
        userRepo.save(user);

        outbox.publish(
            new UserWithdrawalRequested(
                UUID.randomUUID().toString(),
                Instant.now(clock),
                userId.value()),
            "user-events"
        );
    }
}
```

WithdrawalProgress Aggregate (identity 스키마):

```java
// identity-service/.../domain/withdrawal/WithdrawalProgress.java
public class WithdrawalProgress {
    private final UserId userId;
    private final Set<String> remainingServices;     // {"diary", "chat", "learning", "platform"}
    private final Set<String> purgedServices;
    private WithdrawalStatus status;                 // IN_PROGRESS | COMPLETED | TIMEOUT
    private Instant requestedAt;
    private Instant completedAt;

    public void markPurged(String service, Instant now) {
        if (status != WithdrawalStatus.IN_PROGRESS) {
            throw new InvalidWithdrawalTransitionException(status);
        }
        if (!remainingServices.remove(service)) {
            // 이미 처리됨 — 중복 회신 (멱등 처리)
            return;
        }
        purgedServices.add(service);
        if (remainingServices.isEmpty()) {
            this.status = WithdrawalStatus.COMPLETED;
            this.completedAt = now;
        }
    }

    public boolean isComplete() { return status == WithdrawalStatus.COMPLETED; }
}
```

---

## 4. 구독 측 — diary-service (예시, 다른 서비스도 동일 패턴)

```java
// diary-service/.../infrastructure/messaging/UserWithdrawalRequestedListener.java
import app.backend.jamo.contracts.event.identity.UserWithdrawalRequested;
import app.backend.jamo.contracts.event.identity.UserDataPurged;

@Component
public class UserWithdrawalRequestedListener {

    private final ProcessedEventRepository processedEventRepo;
    private final DiaryUserDataPurgeService purgeService;
    private final OutboxEventPublisher outbox;
    private final Clock clock;

    @KafkaListener(topics = "user-events", groupId = "diary-service-withdrawal")
    @Transactional
    public void on(UserWithdrawalRequested event) {
        // 1. 멱등성
        if (processedEventRepo.existsByEventId(event.eventId())) return;

        // 2. 사용자 데이터 일괄 삭제
        purgeService.purgeAllForUser(new UserId(event.userId()));

        // 3. ProcessedEvent 기록
        processedEventRepo.save(new ProcessedEvent(event.eventId(), Instant.now(clock)));

        // 4. 회신 이벤트 발행 (같은 트랜잭션의 outbox)
        outbox.publish(
            new UserDataPurged(
                UUID.randomUUID().toString(),
                Instant.now(clock),
                event.userId(),
                "diary"),
            "user-events"
        );
    }
}
```

`DiaryUserDataPurgeService` 는 diary 스키마의 모든 테이블에서 `user_id = ?` row 를 삭제한다 (diary, comment, sentence_feedback, diarychat_room, diarychat_message 등). 외래키 cascade 또는 명시적 순서 삭제.

---

## 5. 회신 수신 — identity-service

```java
// identity-service/.../infrastructure/messaging/UserDataPurgedListener.java
@Component
public class UserDataPurgedListener {

    private final ProcessedEventRepository processedEventRepo;
    private final WithdrawalProgressRepository progressRepo;
    private final UserRepository userRepo;
    private final OutboxEventPublisher outbox;       // (선택) UserDeleted 후처리
    private final Clock clock;

    @KafkaListener(topics = "user-events", groupId = "identity-service-purge-receiver")
    @Transactional
    public void on(UserDataPurged event) {
        if (processedEventRepo.existsByEventId(event.eventId())) return;

        WithdrawalProgress progress = progressRepo
            .findByUserId(new UserId(event.userId()))
            .orElseThrow(() -> new WithdrawalProgressNotFoundException(event.userId()));

        progress.markPurged(event.service(), Instant.now(clock));
        progressRepo.save(progress);

        if (progress.isComplete()) {
            User user = userRepo.findById(progress.userId())
                .orElseThrow(() -> new UserNotFoundException(progress.userId()));
            userRepo.delete(user);

            // 선택: 후처리 알림
            outbox.publish(
                new UserDeleted(UUID.randomUUID().toString(), Instant.now(clock), event.userId()),
                "user-events"
            );
        }

        processedEventRepo.save(new ProcessedEvent(event.eventId(), Instant.now(clock)));
    }
}
```

---

## 6. 보상 / 미회신 처리

Saga 의 어느 단계에서 실패하면? jamo 는 **자동 보상보다는 운영 알림 + 수동 정리** 로 시작 (ADR-0002 후속 결정). 자동 보상은 추후 ADR.

**미회신 처리 (스케줄러)**:

```java
// identity-service/.../infrastructure/scheduler/WithdrawalTimeoutChecker.java
@Component
public class WithdrawalTimeoutChecker {

    private final WithdrawalProgressRepository progressRepo;
    private final AlertService alertService;        // 운영 슬랙/이메일 등
    private final Clock clock;

    @Scheduled(fixedDelay = 5 * 60 * 1000)          // 5분마다
    public void check() {
        Instant threshold = Instant.now(clock).minus(Duration.ofHours(1));   // 1시간 미회신
        List<WithdrawalProgress> stalled = progressRepo
            .findInProgressOlderThan(threshold);

        for (WithdrawalProgress p : stalled) {
            alertService.notifyStalledWithdrawal(
                p.userId(), p.remainingServices(), p.requestedAt()
            );
        }
    }
}
```

운영자가 알림 받으면:
1. 어느 서비스가 회신 안 했는지 확인 (`progress.remainingServices()`)
2. 해당 서비스 로그/DLQ 확인 (이벤트 처리 실패 원인)
3. 수동 재처리 후 `UserDataPurged` 발행 (운영 도구 또는 직접 SQL)
4. `WithdrawalProgress` 가 COMPLETED 로 전이되며 User HARD DELETE 자동 실행

---

## 7. 멱등성 / 중복 발행 보호

at-least-once Kafka 환경 + Outbox 재발행 가능성:

| 위치 | 보호 |
|---|---|
| 각 서비스의 `UserWithdrawalRequested` 처리 | `ProcessedEvent` 테이블의 `eventId` 체크 → 중복 시 skip |
| 각 서비스의 `UserDataPurged` 발행 | 멱등 키 = `eventId` (UUID). 같은 사용자에 대한 재발행도 OK (identity 측이 `WithdrawalProgress.markPurged` 에서 이미 처리된 service 면 skip) |
| identity 의 `UserDataPurged` 수신 | `ProcessedEvent` + Aggregate 의 idempotent 메서드 |
| 최종 User HARD DELETE | `WithdrawalProgress.status == COMPLETED` 1회만 트리거 |

---

## 8. 자가 검증 체크리스트

- [ ] Saga 이벤트 모두 `contracts/event/identity/` 에 정의
- [ ] `UserWithdrawalRequested`, `UserDataPurged` JavaDoc 에 발행자/구독자/토픽 명시
- [ ] 각 서비스의 데이터 삭제 후 `UserDataPurged` 발행 (Outbox)
- [ ] identity-service 의 `WithdrawalProgress` Aggregate 가 회신 수집/완료 판정
- [ ] `WithdrawalTimeoutChecker` 스케줄러 — 미회신 알림
- [ ] 모든 Consumer 에 `ProcessedEvent` 멱등성
- [ ] 사용자 활동 점수 (Redis ZSET) 도 platform-service 의 purge 단계에서 ZREM
- [ ] PII (이메일, 닉네임 등) 가 다른 서비스의 캐시/Read Model 에 남아있지 않은지 확인

---

## 9. 향후 보강 (후속 ADR)

- 자동 보상: 미회신 시 자동 롤백 (User 상태를 ACTIVE 로 복구) — 위험: 일부 서비스만 삭제된 부분 일관성 상태
- Sagas Orchestration 변형 (학습 비교용)
- `UserDeleted` 이벤트 후처리: 외부 시스템 (SendGrid 구독 해지 등)
- GDPR 우선순위: 즉시 익명화 먼저, 물리 삭제는 retention 후

자세한 결정은 ADR-0002 의 후속 결정 항목 + 별도 ADR (예: ADR-00XX 회원 탈퇴 Saga 보상 정책) 에서.
