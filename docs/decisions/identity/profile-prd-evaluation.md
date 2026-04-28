# Decision: profile 도메인 3 API 일괄 평가 — 응답 스키마 / 화이트리스트 / 빈도 제한 확정

- **상태**: Accepted
- **결정일**: 2026-04-28
- **결정자**: jonghun
- **PR**: `feature/identity-profile-prd-evaluation` (Phase 6-a)
- **관련 ADR**: [ADR-0006 OAuth Provider 통합](../../adr/0006-oauth-provider-integration.md), [ADR-0007 contracts-first 병렬 개발](../../adr/0007-contracts-first-parallel-development.md)
- **선행 결정**:
  - [identity/user-profile-domain-boundary.md](user-profile-domain-boundary.md) — `/me` 조회 단일화 + identity 필드 5종 흡수 강제
  - [identity/clip-domain-removal.md](clip-domain-removal.md) — 4 API → 3 API 축소 (listSavedClips 제외)
  - [contracts/identity-proto](../contracts/) (PR #35) — `UserSummaryService` public-safe 정책 (email/providers/createdAt 노출 X)
- **관련 PRD**: [`prd/profile/getMyProfile.md`](../../prd/profile/getMyProfile.md), [`prd/profile/getProfile.md`](../../prd/profile/getProfile.md), [`prd/profile/updateMyProfile.md`](../../prd/profile/updateMyProfile.md)

## 컨텍스트

profile 도메인 (Phase 6-a) 진입 시점에 3 PRD (`getMyProfile` / `getProfile` / `updateMyProfile`) 의 KEEP/FIX/DROP 일괄 평가를 진행한다. `listSavedClips` 는 [clip-domain-removal](clip-domain-removal.md) 으로 사전에 제외됨.

각 PRD §6 (암묵적 로직) 와 §8 (TODO/Open Questions) 에 다음 11개 결정 포인트가 미해결로 남아 있었다.

| API | Open Question |
|---|---|
| `getMyProfile` | (1) private 필드 포함 정책, (2) **identity 필드 5종 흡수** [선행 결정으로 강제] |
| `getProfile` | (3) viewer-context (follow 여부) 포함 여부, (4) 비공개 프로필 차단 정책, (5) email/providers/createdAt 등 식별 필드 public 노출 여부 |
| `updateMyProfile` | (6) PATCH null 의미, (7) 변경 가능 필드 화이트리스트, (8) 닉네임 변경 빈도 제한, (9) 닉네임 중복 정책 (409), (10) `email` 변경 가능 여부, (11) `providers` 변경 가능 여부 |

본 결정은 11개를 한 번에 해소한다.

## 결정

### 1. `getMyProfile` — KEEP (with FIX)

**판정 근거**: [user-profile-domain-boundary](user-profile-domain-boundary.md) 가 `/me` 조회 단일화로 identity 필드 5종 흡수를 사실상 강제. PRD §1 (Header `@LoginUser`) / §3 (`profileFacade.retrieveMyProfile(userId)`) 의 흐름 자체는 KEEP. 응답 스키마와 private 필드 포함 정책만 FIX.

응답 스키마 (private + identity 5종 흡수):
```json
{
  "id": "uuid",
  "email": "string?",                  // OAuth 가입자는 IdP 미공개 시 null 가능
  "displayName": "string",
  "providers": ["GOOGLE", "KAKAO"],    // List<String>, OAuth 한정 - LOCAL 가입자는 빈 배열
  "createdAt": "2026-04-28T...Z",      // Instant
  "bio": "string?",
  "avatarUrl": "string?",
  "locale": "ko"                        // ISO 639-1, default "ko"
}
```

- **private 필드**: 본인 조회 (`@LoginUser`) 이므로 `email` / `providers` / `createdAt` / `locale` 모두 포함. `getProfile` (public 조회) 와 별도 응답 DTO.
- **identity 필드 매핑**: `id` / `email` / `displayName` / `providers` / `createdAt` 은 `User` aggregate 에서 조회. `bio` / `avatarUrl` / `locale` 은 `Profile` aggregate (Phase 6-b 도입 예정) 에서 조회.

### 2. `getProfile` — KEEP (with FIX)

**판정 근거**: PRD §1 (`@LoginUser` + `userId: Long Positive`) / §3 (`profileFacade.retrieveProfile(loginUserId, userId)`) 의 흐름은 KEEP. 단, 응답 스키마는 [`UserSummaryService` public-safe 정책](../contracts/) 과 **일관**하게 한정. viewer-context (follow 여부) 와 비공개 프로필 차단 정책은 follow 도메인 미존재로 본 PR 의 Non-Goal — 후속 PR 에서 처리.

응답 스키마 (public-safe — `UserSummary` 정합):
```json
{
  "id": "uuid",
  "displayName": "string",
  "bio": "string?",
  "avatarUrl": "string?"
}
```

- **노출 X**: `email` / `providers` / `createdAt` / `locale` — `UserSummary` 와 동일 정책. 식별 가능 정보 (PII) 의 cross-user 노출 차단.
- **viewer-context (follow 여부) 미포함**: follow 도메인이 본 프로젝트에 정의 안 됨. Non-Goal.
- **비공개 프로필 차단 미적용**: 모든 프로필을 조회 가능. 비공개 정책은 follow 도메인 도입 시 함께 결정 (e.g., 비공개 = 팔로워만).
- **404 응답**: 사용자 없음 시 `USER_NOT_FOUND` (404). 0 이하 `userId` 는 `@Positive` validation 으로 400.

### 3. `updateMyProfile` — KEEP (with FIX)

**판정 근거**: PRD §1 (Header `@LoginUser` + Body `@Valid`) / §3 (`profileFacade.updateMyProfile(userId, request.toCommand())`) 의 흐름은 KEEP. 변경 가능 필드 화이트리스트 / PATCH 의미 / 닉네임 정책 / **displayName 의 SoT 위치** 를 FIX.

#### 3.0 displayName SoT — **User aggregate** (Profile 미보유)

**박제**: `displayName` 의 **SoT 는 User aggregate** (`User.displayName`, `User.rename(newName, now)`). Profile aggregate 는 displayName 을 **보유하지 않는다**. 이유:
1. User aggregate 가 이미 OAuth 가입 시 IdP 에서 displayName 을 가져와 보유 (`User.displayName` 필드, `accountType` 무관 — LOCAL 가입자도 동일 필드).
2. Profile 이 displayName 을 별도 보유 시 두 aggregate 간 sync 의무 발생 (AP7 Shared Mutable State, AP8 Cross-Aggregate Transaction).
3. 식별·표시명·이메일·providers 는 *사용자 본인의 정체성* 단일 묶음으로 User AR 에 응집하는 것이 일관 (`user-profile-domain-boundary` 의 user = identity 책임, profile = 외형 책임 정합).

**`UpdateMyProfileService` 의 displayName 변경 경로**: `userRepository.findById(userId).rename(newName, now)` → `userRepository.save(user)` (Profile aggregate 미경유). 본 서비스는 User 와 Profile **두 aggregate** 를 한 트랜잭션에서 다룸 (§4 cross-aggregate 트랜잭션 박제 참조).

빈도 제한 Redis flag key 도 SoT 정합으로 **`user:displayName_changed:{userId}`** 로 명명 (Profile prefix 가 아닌 User prefix).

#### 3.1 변경 가능 필드 화이트리스트

| 필드 | 타입 | 변경 가능? | 소유 AR | 빈도 제한 | 검증 |
|---|---|---|---|---|---|
| `displayName` | String (1-30자, trim) | ✅ | **User** (rename) | **7일 1회** (Redis `user:displayName_changed:{userId}` TTL 7d) | 비어있으면 400, 30자 초과 400 |
| `bio` | String (0-200자) | ✅ | Profile (update) | 무제한 | 200자 초과 400 |
| `avatarUrl` | URL String (nullable) | ✅ | Profile (update) | 무제한 | URL 형식 검증 (http/https), 길이 ≤ 500 |
| `locale` | String (ISO 639-1 — `ko` / `en` 등) | ✅ | Profile (update) | 무제한 | enum 화이트리스트 (`ko` / `en` 우선, 추후 확장) |
| `email` | — | ❌ | (User SoT, IdP) | — | identity 책임. OAuth 가입자는 IdP 가 SoT, LOCAL 가입자는 별도 endpoint (후속) |
| `providers` | — | ❌ | (User OAuthIdentity) | — | auth 흐름 (OAuth 연결/해제 후속 endpoint) |
| `id` / `createdAt` | — | ❌ (불변) | (User) | — | — |

#### PATCH null 의미

- **null = 변경 없음** (no-op).
- **빈 문자열 (`""`)** = `displayName` 의 경우 400. `bio` 의 경우 ""→null 정규화 (선택사항이라 비워둠).
- **명시적 `null` payload** vs **필드 부재** 구분 필요 — Jackson 기본 동작상 둘 다 null 로 deserialize 되므로 동일 처리 (변경 없음). PATCH-with-explicit-null 은 본 도메인에 미적용.

#### 닉네임 정책

- **변경 빈도 제한**: 7일 1회. flag 가 존재하면 `400 DISPLAY_NAME_CHANGE_TOO_FREQUENT`.
- **중복 정책**: `displayName` 은 **고유성 제약 없음** — 유저 식별은 `id` 가 SoT, displayName 은 표시 정보. 같은 displayName 허용 (Discord / Telegram 패턴).
- **PRD §5 의 "닉네임 중복 등 도메인 규칙 → 409"** → DROP. 409 미발생, FIX 로 명시 변경.

#### 응답 스키마

`getMyProfile` 과 **동일 스키마** (8 필드 = identity 5 + 외형 3 + locale). 클라이언트가 PATCH 후 바로 갱신된 상태를 사용 가능. 응답 합성은 **commit 후 `RetrieveMyProfileService.retrieve(userId)` 재호출** 패턴 — update 트랜잭션과 read 트랜잭션을 분리해 update 의 부수 효과(예: 이벤트 핸들러의 추가 변경)도 응답에 반영.

### 4. UpdateMyProfileService — cross-aggregate 트랜잭션 박제

`UpdateMyProfileService.update(userId, command)` 는 **한 트랜잭션** 안에서 다음을 모두 수행한다.

```
@Transactional
1. User user = userRepository.findById(userId)
   if command.displayName != null:
       displayNameChangeRateLimiter.check(userId)        // 7일 1회 flag
       user.rename(command.displayName, clock.now())
       displayNameChangeRateLimiter.markChanged(userId)  // SETEX TTL 7d
   userRepository.save(user)

2. Profile profile = profileRepository.findById(userId)
   profile.update(command.bio, command.avatarUrl, command.locale, clock.now())
   profileRepository.save(profile)

(commit)

3. return retrieveMyProfileService.retrieve(userId)  // 별도 read 트랜잭션
```

**근거**:
- User 와 Profile 은 **같은 BC** (identity-service) 의 **1:1 강결합 aggregate**. `/me` PATCH 의 *all-or-nothing* 의미가 사용자에게 자연스럽다 — 부분 성공 시 응답 8 필드의 일관성이 깨짐.
- Vernon, *Implementing Domain-Driven Design* Ch.10 *Rule of Thumb 2 'Modify One Aggregate per Transaction'* 의 명시적 예외 — "*single transactional consistency within one BC for tightly coupled invariants*". 분산 시스템 일관성 (eventual consistency between BCs) 가 아니라 단일 BC 내 강결합 1:1 aggregate 의 단일 트랜잭션 허용 패턴.
- DB 레벨 FK constraint 는 미사용 (ADR-0005) — 일관성은 Application 트랜잭션과 도메인 invariant 가 보장.
- CLAUDE.md "Application Service 가 다른 Application Service 직접 호출 금지" 미위반 — 한 Application Service 가 두 Domain (User / Profile) 을 호출하는 패턴.

**경계**: 본 예외는 **/me PATCH 한 곳 한정**. 다른 use case 에서 cross-aggregate 트랜잭션 도입 시 별도 결정 박제 필수. Saga / 보상 트랜잭션이 일반 패턴.

## 근거

### 책임 분리
- `getMyProfile` 은 본인 조회 → **모든 필드 포함**. `getProfile` 는 cross-user 조회 → **public-safe 한정**. 같은 도메인의 두 endpoint 가 응답 DTO 를 분리하는 게 명확.
- `email` / `providers` 는 **identity 책임** — profile 의 update 화이트리스트에서 제외. 이는 `user-profile-domain-boundary` 의 *user = write-only credential, profile = read+update display* 정책의 자연스러운 귀결.

### `UserSummary` 정합
- PR #35 의 `UserSummaryService.GetUserSummary` 는 cross-service gRPC public-safe 정책 (id / displayName / status). HTTP `getProfile` 도 같은 public-safe 의미를 가지므로 노출 필드를 동일하게 한정.
- 단, HTTP 는 `bio` / `avatarUrl` 추가 (UserSummary 는 표시명만 필요한 platform 용도라 외형 미포함). 즉 *public-safe = 식별 정보 비공개* 라는 의미는 동일하되, 외형은 HTTP 측이 더 풍부.
- **`locale` 미노출의 본질적 근거**: PII 가 아니라 *cross-user 도메인 의미 부재*. 조회자의 UI 는 조회자 본인의 locale 로 결정되므로 다른 사용자의 locale 을 알 도메인 의미 없음. UserSummary 정합은 부수 효과. 후속 PR (예: `PublicProfileResponse` 에 timezone / theme 추가 검토) 에서 "PII 라서 뺐다" 는 잘못된 해석 차단.

### displayName 변경 빈도 7일 1회
- 사용자 검색 / 멘션 / 채팅방 표시 일관성 보호. 매일 변경 시 다른 사용자가 식별 어려움.
- 기술 구현: Redis SETEX (TTL 7일) — `EmailValidatedFlag` 와 동일 패턴, 운영 단순화.
- 운영 정책 (e.g., VIP 면제) 은 후속 PR 에서 별도 결정.

### `displayName` 고유성 제약 미적용
- `UserStatus` enum 정책 (ADR-0006) 과 일관 — *식별은 id, 표시는 자유*. 동명이인 가능.
- 닉네임 중복 검증을 두면 가입/변경 시 race condition + 등록 광고 trigger 위험. 식별 책임을 한 곳 (id) 에 집중하는 게 도메인 단순.
- **후속 도메인의 retroactive 위험 인지**: 멘션 / 검색 / 차단 / 알림 등 후속 도메인 도입 시에도 식별 키는 `userId` (UUID) 로 일관 유지. displayName fuzzy 검색은 hint 역할만 (`LIKE '%name%'` 인덱스 비효율 허용 또는 검색 전용 Read Model). 만약 `@username` 형태의 unique handle 이 비즈니스 요구로 등장하면 displayName 과 별도 `Handle` VO 를 신설 (Twitter `@handle` vs `name` 분리 패턴) — displayName 자체에 unique 제약을 retroactive 추가하지 않는다.

### viewer-context (follow 여부) Non-Goal
- follow 도메인이 본 백엔드 13 도메인에 부재. 비공개 프로필 차단도 동일.
- 추후 follow 도메인 도입 시 `getProfile` 응답에 `isFollowing` / `isFollowedBy` 추가 + 비공개 프로필 차단 정책 함께 결정.

## 결과 및 영향

### PRD §9 갱신 (본 PR)

| PRD | 판정 | 변경 사항 (FIX) |
|---|---|---|
| `getMyProfile.md` | **KEEP+FIX** | §1 (정상) / §2 응답 스키마 8 필드 / §6 private 필드 포함 명시 / §8 Open Question 해소 |
| `getProfile.md` | **KEEP+FIX** | §2 응답 스키마 4 필드 (public-safe) / §6 viewer-context 미포함 / §8 Open Question 일부 해소 (follow 후속) |
| `updateMyProfile.md` | **KEEP+FIX** | §1 화이트리스트 명시 / §2 응답 스키마 8 필드 / §6 PATCH null 의미 / §8 화이트리스트 + 빈도 제한 + 중복 정책 모두 해소 |

### 코드 영향 (Phase 6-b 이후, 본 PR 범위 외)

#### Domain
- `Profile` aggregate root — **shared identifier 패턴** (IDDD Ch.10): 식별자는 `UserId` 와 **같은 값** (별도 `ProfileId` VO 신설 X). 1:1 매핑이 도메인 invariant 로 보장되며, 코드는 `Profile.id == User.id` 로 표현.
- `Profile` 필드: `bio` (Bio VO) + `avatarUrl` (AvatarUrl VO) + `locale` (Locale VO). **displayName 미보유** (§결정 #3.0 — User SoT). `displayName` 은 응답 합성 시 `User` 에서 조회.
- VO: `Bio` (0-200) / `AvatarUrl` (URL 검증, ≤500) / `Locale` (enum-like 화이트리스트). `DisplayName` VO 는 User aggregate 측 신규 (existing User.displayName 을 String → VO 격상 — Phase 6-b 첫 슬라이스에서 함께).
- Domain Service: 없음 (단순 update 라 aggregate 메서드로 충분)
- Port: `DisplayNameChangeRateLimiter` (Redis flag store) — User aggregate 측 port (profile 디렉토리 X). 위치는 `domain/repository/` 또는 `domain/port/`.
- **이벤트 발행 미채택**: 본 PR 시점 platform-service 가 placeholder 라 Read Model 부재. 표시명 cross-service 조회는 **gRPC `UserSummaryService` 동기 호출만** 사용 (PR #35). `DisplayNameChanged` Kafka 이벤트는 본 PR 결정 범위 밖 — Phase 6-b 또는 platform-service 가 Read Model 도입 시점에 별도 결정 (Outbox + Kafka 추가 비용 정당화 필요). 본 PR 박제: User/Profile 어느 aggregate 도 도메인 이벤트 미수집.

#### Application
- `RetrieveMyProfileService` (private 응답 — User + Profile 합성)
- `RetrieveProfileService` (public-safe 응답 — User.displayName + Profile.bio/avatarUrl 합성)
- `UpdateMyProfileService` — §결정 #4 cross-aggregate 트랜잭션 박제 참조. `User.rename` (displayName 변경 시) + `Profile.update` (bio/avatarUrl/locale) 한 트랜잭션 + commit 후 `RetrieveMyProfileService.retrieve` 재호출로 응답 합성.

#### Infrastructure
- `ProfileJpaEntity` + Flyway V4 — `profiles` 테이블 (`user_id` PK 겸 FK 인덱스, `bio` / `avatar_url` / `locale`). **`display_name` 컬럼 미생성** (User SoT). User SoT 정합으로 1:1 매핑.
- `ProfileMapper` (Domain ↔ Jpa)
- `RedisDisplayNameChangeRateLimiterAdapter` (Redis SETEX, key **`user:displayName_changed:{userId}`** TTL 7d) — User SoT 정합으로 prefix 는 `user:`. 어댑터 위치는 `infrastructure/redis/` 또는 동등.
- (참고) `User.displayName` 컬럼은 PR6 `users` 테이블에 이미 존재. Phase 6-b 슬라이스에서 `User.rename` 메서드 + DisplayName VO 격상.

#### Presentation
- `ProfileController` — 3 endpoint (`GET /me` / `GET /{userId}` / `PATCH /me`)
- DTO: `MyProfileResponse` (8 필드) / `PublicProfileResponse` (4 필드) / `UpdateMyProfileRequest` (4 필드 nullable) / `UpdateMyProfileResponse` = `MyProfileResponse` 재사용
- `ProfileErrorCode` — `DISPLAY_NAME_CHANGE_TOO_FREQUENT` (400) / `USER_NOT_FOUND` (404) / `INVALID_LOCALE` (400) / `INVALID_AVATAR_URL` (400)
- 모든 endpoint 에 `@SecurityRequirement(name = "BearerJwt")` (CLAUDE.md OpenAPI 의무)

### 후속 구현 검토 사항 (Phase 6-b 슬라이스 진입 시 답할 것)

본 결정 박제 범위 밖이지만 **구현 PR 에서 반드시 답해야 하는 작은 결정** 들 — 잊혀지지 않도록 명시.

- **`markChanged` 호출 위치 (RDB 트랜잭션 vs AFTER_COMMIT)**: §결정 #4 의사코드는 `markChanged` 를 `@Transactional` 안에 두지만, Redis 는 RDB 트랜잭션에 참여하지 않음 — `User.rename` 성공 후 `Profile.update` 실패로 RDB rollback 시 Redis flag 만 살아남아 빈도 제한이 *의도치 않게 소진* 될 위험. 옵션: (a) 그대로 두고 모니터링, (b) `@TransactionalEventListener(AFTER_COMMIT)` 으로 옮김. Phase 6-b 첫 슬라이스에서 결정 + 결정 문서 박제.
- **Profile 생성 시점 (회원가입 시 vs lazy)**: 본 결정 §4 의사코드 `profileRepository.findById(userId)` 가 null 인 경우 처리 미명시. 옵션: (a) 회원가입 (`RegisterUser` / OAuth callback) 시 빈 Profile 자동 생성, (b) 첫 PATCH 시 lazy 생성. Phase 6-b 첫 슬라이스에서 결정.

### Non-Goals (본 결정에서 다루지 않음)

- **follow 도메인** / 비공개 프로필 차단 — follow 도메인 도입 시 별도 결정.
- **avatarUrl 의 업로드 vs URL 직접 입력** — 본 결정은 URL 입력만. 파일 업로드는 후속 endpoint (`POST /api/v1/profiles/me/avatar`).
- **`DisplayNameChanged` Kafka 이벤트 도입** — §결과및영향 #Domain 박제대로 본 결정 범위 밖. platform-service Read Model 도입 시점에 별도 결정.
- **`Handle` VO 도입** — `displayName` 고유성이 비즈니스 요구로 등장 시 displayName 과 별도 VO 신설 (Twitter 패턴). 본 결정은 *현 시점 미도입* 만 박제.
- **locale 화이트리스트 확장** — 초기 `ko` / `en`. 추가 언어는 운영 결정.
- **민감 정보 (`email`) 마스킹** — `getMyProfile` 은 본인 조회라 마스킹 X. 만약 운영 모니터링/로깅에서 email 노출 차단이 필요하면 별도 결정.
- **VIP / 운영자 displayName 변경 면제** — 운영 정책 후속.
- **`email` 변경 endpoint** — LOCAL 가입자 한정 별도 endpoint (`PATCH /api/v1/users/me/email`) 후속. OAuth 가입자는 IdP 가 SoT 라 변경 불가.
- **OAuth 연결/해제 endpoint** — `POST /api/v1/auth/oauth/{provider}/link` / `DELETE` 후속.

## 참고

- [ADR-0006 §결정 4](../../adr/0006-oauth-provider-integration.md) — OAuth 가입 시 email 자동 링크 거부 (UNIQUE 제약 미설정)
- [`docs/decisions/identity/user-profile-domain-boundary.md`](user-profile-domain-boundary.md) — user 도메인 write-only 격리, profile read 책임 흡수
- [`docs/decisions/identity/clip-domain-removal.md`](clip-domain-removal.md) — 4 API → 3 API 축소 사유
- [`docs/decisions/identity/local-credential-modeling.md`](local-credential-modeling.md) — `account_type` / `password_hash` 필드 (LOCAL 가입자의 `providers: []` 빈 배열 응답 근거)
- 일반 패턴: Discord / Telegram (displayName 비고유, 식별은 id) / Twitter (PATCH 부분 업데이트 + null=변경 없음)
