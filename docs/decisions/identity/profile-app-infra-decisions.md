# Decision: profile App+Infra 구현 결정 — markChanged 위치 / Profile 생성 시점 / 응답 합성

- **상태**: Accepted
- **결정일**: 2026-04-28
- **결정자**: jonghun
- **PR**: `feature/identity-profile-app-infra` (Phase 6-b-b)
- **선행 결정**:
  - [identity/profile-prd-evaluation.md](profile-prd-evaluation.md) — Phase 6-a 박제 (응답 스키마 / 화이트리스트 / 빈도 제한 / shared identifier / cross-aggregate 트랜잭션 박제 / gRPC only)
  - [identity/clip-domain-removal.md](clip-domain-removal.md) — 4 → 3 API 축소
  - [identity/user-profile-domain-boundary.md](user-profile-domain-boundary.md) — `/me` 단일화
- **관련 ADR**: [ADR-0005 JPA 연관관계 / FK constraint 미사용](../../adr/0005-no-jpa-association-no-fk-constraint.md)

## 컨텍스트

Phase 6-b-a (Domain + Port) 머지 (PR #42) 후 Application + Infrastructure 진입 직전, [profile-prd-evaluation §후속 구현 검토 사항](profile-prd-evaluation.md) 의 2건 미결 결정 + 추가 1건을 확정한다.

| # | 결정 항목 | 옵션 |
|---|---|---|
| 1 | `markChanged` 호출 위치 | RDB 트랜잭션 내부 vs `@TransactionalEventListener(AFTER_COMMIT)` |
| 2 | Profile 생성 시점 | Eager (회원가입 시) / Lazy (PATCH/조회 시) / Lazy + Read 기본값 |
| 3 | 응답 합성 패턴 | 단일 트랜잭션 합성 / 별도 read 트랜잭션 재조회 |

## 결정 #1 — `markChanged` Redis 호출 = `@TransactionalEventListener(AFTER_COMMIT)` (옵션 Y)

### 검토한 옵션

| 옵션 | 흐름 | 단점 |
|---|---|---|
| **X. RDB 트랜잭션 내부** | `User.rename` → `markChanged` → `Profile.update` 한 트랜잭션 | RDB rollback 시 Redis flag 잔존 → 의도치 않은 7일 빈도 제한 소진 (사용자 불만) |
| **Y. `AFTER_COMMIT` 이벤트** ⭐ | 트랜잭션 안에서 `DisplayNameChanged` Spring ApplicationEvent 발행 → `@TransactionalEventListener(phase = AFTER_COMMIT)` 핸들러가 `markChanged` 호출 | RDB rollback 시 flag 도 안 들어감 (정합성). commit 후 Redis 호출 실패 시 빈도 제한 미적용 — *덜 엄격한* 실패 모드 (사용자 유리) |

### 결정 — **Y**: AFTER_COMMIT 이벤트

**근거**:
1. **사용자 영향 비대칭** — 옵션 X 의 실패 모드 (rollback + flag 잔존) 는 사용자가 *7일간 변경 차단* 으로 체감 (불만). 옵션 Y 의 실패 모드 (commit + Redis 다운 시 flag 미설정) 는 사용자가 *제한 없이 변경 가능* — 덜 엄격하지만 사용자 친화적. 정책 위반 비용이 사용자 불편 비용보다 낮다.
2. **ddd-architect 1차 리뷰 M1 권장** — 동일 이슈 지적, AFTER_COMMIT 패턴 권장.
3. **본 이벤트는 cross-service 가 아닌 *서비스 내부 부수효과 트리거*** — Spring `ApplicationEventPublisher` 의 메모리 이벤트, **Kafka 미경유**. `profile-prd-evaluation.md §결과및영향 #Domain` 의 "이벤트 미채택" 박제는 *Kafka 도메인 이벤트* 한정 의미이며, 본 결정의 메모리 이벤트와 충돌 없음.

### 구현

**Domain Event** (`identity-service/.../domain/event/`):
```java
public record DisplayNameChanged(UserId userId, Duration ttl) { }
```

**이벤트 발행** (`UpdateMyProfileService`):
```java
@Transactional
public MyProfileResult update(UpdateMyProfileCommand command) {
    if (command.displayName() != null) {
        rateLimiter.check(userId);                                  // flag 검사 — 트랜잭션 진입
        user.rename(new DisplayName(command.displayName()), now);
        eventPublisher.publishEvent(new DisplayNameChanged(userId, DISPLAY_NAME_CHANGE_TTL));
    }
    // ... profile.update + save
}
```

**핸들러** (`infrastructure/event/` 또는 `application/event/`):
```java
@Component
public class DisplayNameChangeRateLimiterListener {
    private final DisplayNameChangeRateLimiter rateLimiter;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DisplayNameChanged event) {
        rateLimiter.markChanged(event.userId(), event.ttl());
    }
}
```

**TTL**: `Duration.ofDays(7)` — `UpdateMyProfileService` 의 상수.

## 결정 #2 — Profile 생성 시점 = **Lazy + Read 기본값** (옵션 C)

### 검토한 옵션

| 옵션 | 흐름 | 단점 |
|---|---|---|
| **A. Eager** (회원가입 시 자동) | `OAuthCallbackService` + `UserRegistrationService` 양쪽에서 `Profile.create + save` 호출 | user 도메인 코드 수정. Profile 의존성이 user write 경로에 침투. cross-aggregate 트랜잭션이 user 측에도 등장 |
| **B. Lazy** (조회/PATCH 시 자동 생성) | Read/Update service 모두 `findById` 가 비어있으면 자동 생성 | Read 가 write 일으킴 (GET 의 idempotency 깨짐) |
| **C. Lazy + Read 기본값** ⭐ | Read service: Profile 부재 시 *기본값 응답* (DB write 없음). Update service: 부재 시 `Profile.create` 후 update | Read service 가 Optional<Profile> 처리 + 기본값 합성 코드 약간 |

### 결정 — **C**: Lazy + Read 기본값

**근거**:
1. **Read endpoint 의 idempotency 보존** — `GET /me`, `GET /{userId}` 가 사용자 상태에 따라 DB write 를 일으키지 않음. 캐시 정책 / RPS 측정 / 모니터링 모두 read 가 read 답게.
2. **user 도메인 무수정** — `OAuthCallbackService` / `UserRegistrationService` 변경 0건. 본 슬라이스 분량 최소화 + user 도메인의 단일 책임 (가입/검증) 격리 유지.
3. **Update 의 lazy create 는 자연스러움** — write endpoint 에서 첫 PATCH 가 곧 첫 등록. *해당 사용자가 외형 정보를 명시적으로 가지기 시작한 시점* 이라는 자연스러운 의미.
4. **shared identifier 패턴과 정합** — Profile.id == User.id 이므로 *User 가 있으면 Profile 도 있다* 는 invariant 가 옵션 A 의 경우 *DB 차원 강제*. C 는 *Application 차원 가시 invariant* (Read 시 Optional 처리 + Update 시 lazy create) 로 약화. 이는 *Profile 미생성 = 사용자가 외형 미설정* 이라는 명시적 도메인 의미로 해석 가능.

### 구현

**Read service** (`RetrieveMyProfileService`):
```java
@Transactional(readOnly = true)
public MyProfileResult retrieve(RetrieveMyProfileQuery query) {
    User user = userRepository.findById(query.userId())
            .orElseThrow(() -> new UserNotFoundException(...));
    Optional<Profile> profileOpt = profileRepository.findById(query.userId());

    return new MyProfileResult(
            user.id(),
            user.email().orElse(null),
            user.displayName(),
            user.oauthIdentities().stream().map(OAuthIdentity::provider).toList(),
            user.createdAt(),
            profileOpt.flatMap(Profile::bio).orElse(null),
            profileOpt.flatMap(Profile::avatarUrl).orElse(null),
            profileOpt.map(Profile::locale).orElse(Locale.DEFAULT));  // 기본값
}
```

**Update service** (`UpdateMyProfileService`):
```java
Profile profile = profileRepository.findById(userId)
        .orElseGet(() -> Profile.create(userId, now));   // lazy create
// ... profile 부분 변경 ...
profileRepository.save(profile);                          // 첫 등록 또는 갱신
```

### Non-Goals

- **회원가입 시점에 Profile row 생성** — 본 결정으로 미적용. 추후 운영 모니터링 (Profile row 수 vs User row 수 갭) 또는 성능 이슈 (PATCH 시 INSERT/UPDATE 경합) 발견 시 옵션 A 로 전환 검토.

## 결정 #3 — 응답 합성 = **단일 트랜잭션 합성 반환**

### 컨텍스트

[profile-prd-evaluation §결정 #4](profile-prd-evaluation.md) 의사코드:
```
... (commit)
3. return retrieveMyProfileService.retrieve(userId)  // 별도 read 트랜잭션
```

이 박제의 *별도 read 트랜잭션* 의도는 "*update 의 부수 효과 (이벤트 핸들러의 추가 변경) 를 응답에 반영*". 그러나 본 슬라이스의 이벤트 핸들러는 `markChanged` 만 (Redis 변경, **DB 변경 X**) 이므로 응답 합성에 영향 없음.

### 결정 — 단일 트랜잭션 합성

`UpdateMyProfileService.update(...)` 가 `@Transactional` 안에서 `User.rename` + `Profile.update` 를 적용한 직후 같은 메서드에서 `MyProfileResult` 를 합성하여 반환. **별도 retrieve 호출 X**.

### 근거

1. **이벤트 핸들러가 DB 미변경** — 결정 #1 의 핸들러는 `markChanged` (Redis only). RDB 상태가 commit 시점과 동일하므로 *재조회로 얻을 추가 정보 없음*.
2. **단순성** — Service 메서드 분리 / propagation REQUIRES_NEW / Controller 측 두 service 호출 불필요.
3. **Phase 6-a §F3 박제 의도 보존** — *commit 후 시점의 정확한 상태 반영* 이라는 의미는 단일 트랜잭션 안에서도 동일하게 성립 (이벤트가 RDB 미변경이므로). 박제 정정 불필요, 의미 해석만 명시.

### 영향

`profile-prd-evaluation.md §결정 #4` 의사코드는 *retrieve 재호출* 표현이지만 본 결정은 *직접 합성* 으로 단순화. 두 표현은 *결과 동치* (이벤트 핸들러가 RDB 미변경이라는 본 슬라이스 조건 하).

향후 도메인 이벤트 핸들러가 RDB 변경을 일으키면 (e.g., audit log 테이블 insert) *재조회 패턴 재검토* 필요 — 후속 결정 박제 cross-reference.

### 락 순서 박제 (code review M4)

본 BC 안에서 User + Profile 두 aggregate 를 다루는 모든 코드는 **항상 User aggregate 먼저, Profile 나중** 순서로 row 락을 획득한다. `UpdateMyProfileService.update` 가 이 순서를 따른다.

**근거**: 향후 다른 use case (예: 회원 탈퇴 — User 의 `deactivate()` + Profile 정리) 가 추가될 때 동일 순서를 따라야 deadlock 회피. 같은 서비스가 두 aggregate 를 *반대 순서로* 락 획득하는 경로가 등장하면 cross-locking 발생.

**경계**: 본 락 순서 규칙은 cross-aggregate 트랜잭션 (decisions §결정 #4 예외) 한정. 단일 aggregate use case 는 무관.

## Flyway V4 — `profiles` 테이블

### DDL

```sql
CREATE TABLE profiles (
    user_id BINARY(16) NOT NULL,
    bio VARCHAR(200) NULL,
    avatar_url VARCHAR(500) NULL,
    locale VARCHAR(8) NOT NULL DEFAULT 'ko',
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 정합 박제

- **`display_name` 컬럼 미생성** — User SoT (PR #42 의 박제 정정 참조). 응답 합성 시 `users` 테이블의 `display_name` 을 합쳐 반환.
- **FK constraint 미사용** — ADR-0005 정합. `user_id` 가 외래 ID 만 보유, 인덱스는 PK 자동 인덱스로 충분.
- **`locale DEFAULT 'ko'`** — Domain `Locale.DEFAULT` 와 정합. lazy create 시 `Profile.create(userId, now)` 가 `Locale.DEFAULT` 사용.
- **TIMESTAMP(3)** — 밀리초 정밀도 (`users` 테이블 정합).
- **VARCHAR 길이** — `bio 200` (Bio.MAX_LENGTH), `avatar_url 500` (AvatarUrl.MAX_LENGTH), `locale 8` (ISO 639-1 + 향후 country suffix 여유).

## 결과 및 영향

### 코드 변경 (본 PR)

#### Domain Event (신규 디렉토리 `domain/event/`)
- `DisplayNameChanged` record (userId + ttl)

#### Application
- `application/dto/`: `RetrieveMyProfileQuery`, `RetrieveProfileQuery`, `UpdateMyProfileCommand`, `MyProfileResult` (8 필드), `PublicProfileResult` (4 필드)
- `application/service/`: `RetrieveMyProfileService`, `RetrieveProfileService`, `UpdateMyProfileService`
- `application/event/`: `DisplayNameChangeRateLimiterListener` (`@TransactionalEventListener(AFTER_COMMIT)`)

#### Infrastructure
- `infrastructure/persistence/entity/ProfileJpaEntity` (`@Entity`, `@Table(name="profiles")`)
- `infrastructure/persistence/repository/SpringDataProfileRepository` (interface), `ProfileRepositoryImpl`
- `infrastructure/persistence/mapper/ProfileMapper`
- `infrastructure/redis/RedisDisplayNameChangeRateLimiterAdapter` (Redis SETEX, key `user:displayName_changed:{userId}`)

#### Migration
- `src/main/resources/db/migration/V4__create_profile.sql`

### Non-Goals

- **회원가입 시 Profile row eager 생성** — 본 결정 옵션 C 로 후속.
- **Presentation layer (Controller / DTO / ErrorCode / ExceptionHandler)** — Phase 6-b-c 별도 PR.
- **avatarUrl 파일 업로드 endpoint** — 별도 후속.
- **운영 모니터링 (Profile row 수 vs User row 수 갭)** — Phase 6-b 머지 후 운영 관찰.

## 참고

- [profile-prd-evaluation.md](profile-prd-evaluation.md) — Phase 6-a 박제 (선행)
- [ADR-0005](../../adr/0005-no-jpa-association-no-fk-constraint.md) — FK constraint 미사용
- [user-profile-domain-boundary.md](user-profile-domain-boundary.md) — user write-only / profile read+update
