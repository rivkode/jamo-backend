# Read Model 동기화 — platform-service 활동 랭킹 ZSET

`module-boundary/SKILL.md` §7 의 상세 시나리오.

각 Java 서비스(diary, chat, comment, learning)에서 발생하는 사용자 활동을 **Outbox 패턴 → Kafka → platform-service 가 구독 → Redis Sorted Set(ZSET) 갱신** 흐름으로 글로벌 랭킹을 만든다.

> SoT 는 각 서비스의 활동 데이터(또는 Kafka 이벤트 로그). Redis ZSET 은 **Read Model** (조회 전용 캐시). 랭킹 휘발 시 SoT 로부터 재구축 가능해야 함.

---

## 1. 흐름

```
[diary-service]                   [chat-service]                [comment 흐름]            [learning-service]
일기 작성 (Diary.create)          AI 채팅 생성                    댓글 작성                   sentence/word 학습 (활성화 시)
  ↓                                  ↓                              ↓                          ↓
Outbox: ActivityHappened          Outbox: ActivityHappened      Outbox: ...                 Outbox: ...
{type=DIARY_CREATED,              {type=CHAT_GENERATED, ...}    {type=COMMENT_CREATED,...}
 points=10, ...}
  ↓ Kafka topic "activity-events"
                                  ┌──────────────────────────────┐
                                  ▼                              │
                          [platform-service]                     │
                          ActivityHappenedListener               │
                            1. ProcessedEvent 멱등성              │
                            2. 점수 정책 (ActivityScorePolicy)     │
                            3. Redis: ZINCRBY ranking:global     │
                                       ZINCRBY ranking:weekly:.. │
                            4. ProcessedEvent 저장                │
                                                                 │
                          GET /api/v1/rankings/global             │
                            ZREVRANGE ranking:global 0 99 WITHSCORES
                            UserSummaryService gRPC (identity) ─┘
                            → 닉네임/프로필 사진 조인
                            → 응답
```

---

## 2. 이벤트 정의

```java
// contracts/event/activity/ActivityHappened.java
package app.backend.jamo.contracts.event.activity;

import java.time.Instant;

/**
 * 사용자 활동 발생 시 각 도메인 서비스가 발행.
 *
 * <p>발행자: diary, chat, comment(diary), learning(활성화 시), identity(가입 보너스 등)
 * <p>구독자: platform-service (랭킹 ZSET 갱신)
 * <p>토픽: {@code activity-events}
 */
public record ActivityHappened(
    String eventId,         // 멱등성 키
    Instant occurredAt,
    String userId,
    ActivityType type,      // 활동 종류 enum
    int points              // 양수: 가산, 음수: 차감 (활동 취소)
) {
    public ActivityHappened {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId");
        if (type == null) throw new IllegalArgumentException("type");
    }
}
```

```java
// contracts/event/activity/ActivityType.java
public enum ActivityType {
    USER_REGISTERED,
    DIARY_CREATED, DIARY_DELETED,
    COMMENT_CREATED, COMMENT_DELETED,
    DIARY_LIKED, DIARY_UNLIKED,
    CHAT_GENERATED,
    VOICE_INPUT_PROCESSED,
    SENTENCE_FEEDBACK_REQUESTED,
    SENTENCE_FEEDBACK_ACCEPTED,
    SENTENCE_LEARNED,
    WORD_REVIEWED;
}
```

> 활동별 점수 가중치는 platform-service 의 `ActivityScorePolicy` 가 보유 (ADR-0002 후속 결정).

---

## 3. 발행 측 (예: diary-service)

```java
// diary-service/.../application/DiaryService.java
@Service
public class DiaryService {

    private final DiaryRepository diaryRepo;
    private final OutboxEventPublisher outbox;
    private final Clock clock;

    @Transactional
    public DiaryId create(CreateDiaryCommand cmd) {
        Diary diary = Diary.create(cmd.userId(), cmd.content(), Instant.now(clock));
        diaryRepo.save(diary);

        outbox.publish(
            new ActivityHappened(
                UUID.randomUUID().toString(),
                Instant.now(clock),
                cmd.userId().value(),
                ActivityType.DIARY_CREATED,
                /* points 기본값 — platform 정책에 의해 재계산 */ 0),
            "activity-events"
        );

        return diary.id();
    }

    @Transactional
    public void delete(DiaryId diaryId, UserId userId) {
        Diary diary = diaryRepo.findById(diaryId)
            .orElseThrow(() -> new DiaryNotFoundException(diaryId));
        diary.delete(userId);
        diaryRepo.delete(diary);

        outbox.publish(
            new ActivityHappened(
                UUID.randomUUID().toString(),
                Instant.now(clock),
                userId.value(),
                ActivityType.DIARY_DELETED,
                /* 정정 이벤트 */ 0),
            "activity-events"
        );
    }
}
```

> `points = 0` 으로 발행하고 platform 의 정책 표가 결정하는 방식, 또는 발행 측이 미리 계산하는 방식 중 선택. **권고: platform 측 단일 정책** (변경 시 한 곳만).

---

## 4. 구독 측 — platform-service

### 4.1 Listener

```java
// platform-service/.../infrastructure/messaging/ActivityHappenedListener.java
import app.backend.jamo.contracts.event.activity.ActivityHappened;

@Component
public class ActivityHappenedListener {

    private final ProcessedEventRepository processedEventRepo;
    private final ActivityScorePolicy scorePolicy;
    private final RankingZSetUpdater rankingUpdater;
    private final Clock clock;

    @KafkaListener(topics = "activity-events", groupId = "platform-service-ranking")
    @Transactional
    public void on(ActivityHappened event) {
        if (processedEventRepo.existsByEventId(event.eventId())) return;

        int points = scorePolicy.computePoints(event.type());      // DIARY_CREATED → +10, ACCEPTED → +5 등
        rankingUpdater.applyDelta(new UserId(event.userId()), points, event.occurredAt());

        processedEventRepo.save(new ProcessedEvent(event.eventId(), Instant.now(clock)));
    }
}
```

### 4.2 점수 정책 (정책 외부화)

```java
// platform-service/.../domain/activity/ActivityScorePolicy.java
public class ActivityScorePolicy {
    private final Map<ActivityType, Integer> table = Map.ofEntries(
        Map.entry(ActivityType.USER_REGISTERED, 50),
        Map.entry(ActivityType.DIARY_CREATED, 10),
        Map.entry(ActivityType.DIARY_DELETED, -10),
        Map.entry(ActivityType.COMMENT_CREATED, 3),
        Map.entry(ActivityType.COMMENT_DELETED, -3),
        Map.entry(ActivityType.DIARY_LIKED, 1),
        Map.entry(ActivityType.DIARY_UNLIKED, -1),
        Map.entry(ActivityType.CHAT_GENERATED, 2),
        Map.entry(ActivityType.VOICE_INPUT_PROCESSED, 2),
        Map.entry(ActivityType.SENTENCE_FEEDBACK_REQUESTED, 1),
        Map.entry(ActivityType.SENTENCE_FEEDBACK_ACCEPTED, 5),
        Map.entry(ActivityType.SENTENCE_LEARNED, 4),
        Map.entry(ActivityType.WORD_REVIEWED, 1)
    );
    public int computePoints(ActivityType type) {
        return table.getOrDefault(type, 0);
    }
}
```

> 가중치 / 캡(예: 일일 최대 +200) / 보너스 정책의 상세는 ADR-0002 후속 결정.

### 4.3 Redis ZSET 갱신

```java
// platform-service/.../infrastructure/cache/RankingZSetUpdater.java
@Component
public class RankingZSetUpdater {

    private final StringRedisTemplate redis;
    private static final String GLOBAL = "ranking:global";
    private static final DateTimeFormatter WEEKLY_FMT = DateTimeFormatter.ofPattern("YYYYww");

    public void applyDelta(UserId userId, int points, Instant occurredAt) {
        if (points == 0) return;

        String userKey = userId.value();
        String weeklyKey = "ranking:weekly:" + occurredAt.atZone(ZoneOffset.UTC).format(WEEKLY_FMT);

        // 글로벌 영구 + 주간 1주일 TTL
        redis.opsForZSet().incrementScore(GLOBAL, userKey, points);
        redis.opsForZSet().incrementScore(weeklyKey, userKey, points);
        redis.expire(weeklyKey, Duration.ofDays(14));   // 안전 마진
    }
}
```

> 음수 점수 누적으로 score 가 0 이 되어도 ZSET 에서 자동 제거 X. 필요 시 별도 정리 배치.

### 4.4 조회 API

```java
// platform-service/.../presentation/RankingController.java
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingController {

    private final RankingQueryService queryService;

    @GetMapping("/global")
    public List<RankingEntryResponse> globalTop(@RequestParam(defaultValue = "100") int size) {
        return queryService.globalTop(size);
    }

    @GetMapping("/weekly")
    public List<RankingEntryResponse> weekly(@RequestParam(defaultValue = "100") int size) {
        return queryService.weekly(YearWeek.now(), size);
    }
}
```

```java
// platform-service/.../application/RankingQueryService.java
@Service
@Transactional(readOnly = true)
public class RankingQueryService {

    private final StringRedisTemplate redis;
    private final UserSummaryClient userSummaryClient;     // gRPC → identity-service

    public List<RankingEntryResponse> globalTop(int size) {
        Set<ZSetOperations.TypedTuple<String>> entries = redis.opsForZSet()
            .reverseRangeWithScores("ranking:global", 0, size - 1);

        if (entries == null || entries.isEmpty()) return List.of();

        List<String> userIds = entries.stream().map(ZSetOperations.TypedTuple::getValue).toList();
        Map<String, UserSummary> summaries = userSummaryClient.batchGet(userIds);

        int rank = 1;
        List<RankingEntryResponse> result = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> e : entries) {
            UserSummary s = summaries.get(e.getValue());
            result.add(new RankingEntryResponse(
                rank++,
                e.getValue(),
                s != null ? s.nickname() : "(unknown)",
                s != null ? s.profileImageUrl() : null,
                e.getScore() == null ? 0 : e.getScore().longValue()
            ));
        }
        return result;
    }
}
```

---

## 5. 키 정책 (ADR-0002 후속)

| 키 | 용도 | TTL |
|---|---|---|
| `ranking:global` | 전체 누적 랭킹 | 영구 (재구축 가능) |
| `ranking:weekly:{yyyyww}` | 주간 랭킹 | 14일 (안전 마진 포함) |
| `ranking:monthly:{yyyymm}` | 월간 랭킹 (선택) | 60일 |
| `ranking:daily:{yyyymmdd}` | 일간 랭킹 (선택) | 7일 |

---

## 6. 재구축 배치 (ZSET 유실 / 운영 사고 대비)

```java
// platform-service/.../infrastructure/scheduler/RankingRebuildJob.java
@Component
public class RankingRebuildJob {

    private final ProcessedEventRepository processedEventRepo;
    private final RankingZSetUpdater rankingUpdater;
    private final ActivityScorePolicy scorePolicy;

    /** 운영자가 수동 트리거하거나 헬스체크에서 호출. */
    public void rebuild(Instant fromOffset) {
        // 1. 기존 ZSET 키 삭제 (별도 키로 임시 빌드 후 RENAME 권장)
        // 2. Kafka activity-events 토픽을 fromOffset 부터 재처리
        //    (ProcessedEvent 테이블도 같이 비워야 재가산됨)
        // 3. 또는 각 서비스의 활동 SoT 테이블을 SELECT → 점수 계산 → ZINCRBY
    }
}
```

**주의**: Kafka retention 안에서만 재처리 가능. retention 을 넘어가면 각 서비스의 도메인 데이터로부터 재계산 (예: diary-service 에 `SELECT user_id, COUNT(*) FROM diary GROUP BY user_id` 같은 식).

---

## 7. 회원 탈퇴 시 ZSET 정리

회원 탈퇴 Saga(`saga-example.md`) 의 platform-service 단계에서:

```java
// platform-service/.../application/UserDataPurgeService.java
@Transactional
public void purgeAllForUser(UserId userId) {
    // ... shorts, event 처리, feedback 처리 ...

    // Redis ZSET 모든 키에서 사용자 제거
    String userKey = userId.value();
    redis.opsForZSet().remove("ranking:global", userKey);
    // 주간/월간 키 (가능하면 SCAN 으로)
    Cursor<String> cursor = redis.scan(ScanOptions.scanOptions()
        .match("ranking:weekly:*")
        .count(100).build());
    cursor.forEachRemaining(key -> redis.opsForZSet().remove(key, userKey));
}
```

---

## 8. 자가 검증 체크리스트

- [ ] `ActivityHappened` 가 `contracts/event/activity/` 에 정의 + JavaDoc
- [ ] 모든 활동 발생 서비스가 Outbox 로 이벤트 발행
- [ ] platform-service Listener 에 `ProcessedEvent` 멱등성
- [ ] 점수 정책이 platform-service 의 `ActivityScorePolicy` 한 곳에 집중
- [ ] 글로벌 / 주간 / 월간 키 정책 문서화 (ADR / contracts-catalog)
- [ ] ZSET 재구축 배치 (Kafka retention 안 + 도메인 SoT 재계산 양쪽)
- [ ] 회원 탈퇴 시 모든 랭킹 키에서 사용자 제거
- [ ] 랭킹 응답에 사용자 표시명 매핑 (identity-service `UserSummaryService` gRPC)
- [ ] 표시명 조회 캐싱 (선택) 시 stale 정책 명시

---

## 9. 안티패턴

| 안티패턴 | 올바른 방법 |
|---|---|
| ZSET 만 믿고 SoT 재구축 배치 없음 | Kafka retention + 도메인 데이터 재계산 양쪽 준비 |
| 점수 정책이 발행 측마다 흩어짐 | platform-service 단일 `ActivityScorePolicy` |
| ZSET 키 이름 = 도메인 Aggregate 이름 | 명확히 구분 (`Diary` Aggregate vs `ranking:global`) |
| 사용자 탈퇴 시 ZSET 정리 누락 | 탈퇴 Saga 의 platform 단계에서 ZREM |
| 랭킹 표시명을 매번 identity DB 직접 조회 | gRPC `UserSummaryService` 또는 Redis 캐시 Read Model |
| 음수 점수 누적으로 ZSET 정합성 깨짐 | 정정 이벤트 시 명시적 처리 + 정리 배치 |
