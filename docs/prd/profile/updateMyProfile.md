---
api_id: profile.updateMyProfile
http_method: PATCH
path: /api/v1/profiles/me
auth: Y
controller: ProfileApiController.kt
handler: updateMyProfile
status: mined
---

# PATCH /api/v1/profiles/me — 내 프로필 부분 수정

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: `UpdateMyProfileRequest` (`@Valid`) — 화이트리스트 4 필드, 모두 nullable

```json
{
  "displayName": "string?",  // null = 변경 없음. 1-32자, trim (DisplayName VO MAX_LENGTH 정합, ADR-0006 결정 3 truncate 32자)
  "bio": "string?",          // null = 변경 없음. 0-200자
  "avatarUrl": "string?",    // null = 변경 없음. URL ≤500
  "locale": "string?"        // null = 변경 없음. ISO 639-1 화이트리스트 (ko/en 등)
}
```

**변경 가능 필드 화이트리스트** ([identity/profile-prd-evaluation](../../decisions/identity/profile-prd-evaluation.md) §3 박제):

| 필드 | 변경 가능? | 소유 AR | 빈도 제한 | 검증 / 빈 문자열 의미 |
|---|---|---|---|---|
| `displayName` | ✅ | **User** (`User.rename`) | **7일 1회** | 1-32자, trim (existing `DisplayName` VO MAX_LENGTH=32). 빈 문자열 → 400 |
| `bio` | ✅ | Profile | 무제한 | 0-200자. 빈 문자열 → null 정규화 |
| `avatarUrl` | ✅ | Profile | 무제한 | URL (http/https), ≤500자. 빈 문자열 → null 정규화 (아바타 제거) |
| `locale` | ✅ | Profile | 무제한 | enum 화이트리스트 (`ko`/`en` 우선). 빈 문자열 → 400 |
| `email` / `providers` / `id` / `createdAt` | ❌ | (User SoT) | — | 요청 body 무시 (강제 격리) |

## 2. 응답 (Response)
- 성공: `200 OK` + `MyProfileResponse` (`getMyProfile` 와 동일 스키마, 8 필드)

```json
{
  "id": "uuid",
  "email": "string?",
  "displayName": "string",
  "providers": ["GOOGLE", "KAKAO"],
  "createdAt": "2026-04-28T10:00:00Z",
  "bio": "string?",
  "avatarUrl": "string?",
  "locale": "ko"
}
```

PATCH 후 클라이언트가 갱신된 상태를 즉시 사용 가능하도록 본인 프로필 전체를 응답.

## 3. 비즈니스 로직 (요약)

**cross-aggregate 트랜잭션** ([identity/profile-prd-evaluation](../../decisions/identity/profile-prd-evaluation.md) §결정 #4 박제 — User/Profile 같은 BC 1:1 강결합 예외):

```
@Transactional
1. User user = userRepository.findById(userId)
   if command.displayName != null:
       displayNameChangeRateLimiter.check(userId)        // flag 존재 시 400
       user.rename(command.displayName, clock.now())      // User AR 의 책임
       displayNameChangeRateLimiter.markChanged(userId)  // Redis SETEX TTL 7d
   userRepository.save(user)

2. Profile profile = profileRepository.findById(userId)
   profile.update(command.bio, command.avatarUrl, command.locale, clock.now())
   profileRepository.save(profile)

(commit)

3. return retrieveMyProfileService.retrieve(userId)  // 별도 read 트랜잭션, 응답 합성
```

- `displayName` 의 SoT 는 **User aggregate** (Profile 미보유). `User.rename(newName, now)` 가 변경 메서드.
- 응답 합성은 commit 후 `RetrieveMyProfileService.retrieve(userId)` **재호출** — update 와 read 트랜잭션 분리.
- `DisplayNameChanged` 도메인 이벤트는 본 단계에서 **발행하지 않음** (platform-service Read Model 부재 — `UserSummaryService` gRPC 동기 호출만 사용).

## 4. 데이터 의존
- DB read/write: `users` (displayName SoT, `User.rename` 시) + `profiles` (bio / avatar_url / locale)
- Redis: `user:displayName_changed:{userId}` (TTL 7d) — User SoT 정합으로 `user:` prefix
- Kafka: 본 endpoint 발행 이벤트 없음 (`DisplayNameChanged` 미채택, [profile-prd-evaluation](../../decisions/identity/profile-prd-evaluation.md) §결과및영향 #Domain 참조)

## 5. 예외 케이스
- 인증 실패 → 401
- validation 실패 (길이 / URL 형식 / locale 화이트리스트) → 400
- `displayName` 변경 빈도 위반 → `400 DISPLAY_NAME_CHANGE_TOO_FREQUENT`
- ~~닉네임 중복 등 도메인 규칙 → 409~~ — `displayName` 고유성 미적용으로 **DROP** ([identity/profile-prd-evaluation](../../decisions/identity/profile-prd-evaluation.md) §3 박제)

## 6. 암묵적 로직 (Implicit)
- **PATCH null 의미**: `null` (또는 필드 부재) = 변경 없음.
- **빈 문자열 (`""`) 의미**:
  - `displayName` → 400 (비어있으면 안 됨)
  - `bio` → null 정규화 (소개 제거)
  - `avatarUrl` → null 정규화 (아바타 제거)
  - `locale` → 400 (화이트리스트 위반)
- **명시적 `null` payload vs 필드 부재 구분 미적용** — Jackson 기본 deserialization 으로 둘 다 null 처리 (변경 없음).
- **화이트리스트 외 필드 (`email` / `providers` / `id` / `createdAt`)** 는 요청 body 에 와도 무시 — Jackson `@JsonIgnoreProperties(ignoreUnknown = true)` 또는 DTO 가 4 필드만 정의.
- **`displayName` 고유성 제약 없음** — Discord/Telegram 패턴 (식별은 id, 표시는 자유). `Handle` VO 필요 시 별도 신설 (Twitter 패턴).
- **`displayName` 변경 = User aggregate 변경**: profile endpoint 라도 SoT 가 User 라 cross-aggregate 트랜잭션 (§3 박제). [profile-prd-evaluation §결정 #3.0 / #4](../../decisions/identity/profile-prd-evaluation.md) 참조.

## 7. 호출자 (Clients)
- 모바일 / 웹 (프로필 편집 화면)

## 8. TODO / Open Questions
- ~~변경 가능 필드 화이트리스트~~ → `displayName / bio / avatarUrl / locale` 4 필드 (해소)
- ~~닉네임 변경 빈도 제한~~ → 7일 1회 (Redis flag) (해소)
- ~~PATCH null 의미~~ → null = 변경 없음 (해소)
- ~~닉네임 중복 정책 (409)~~ → 고유성 미적용으로 DROP (해소)

## 9. KEEP/DROP/FIX 분류

**판정: KEEP (with FIX)** — 2026-04-28, [identity/profile-prd-evaluation](../../decisions/identity/profile-prd-evaluation.md) 와 함께 결정

PRD 의 핵심 흐름 (`@LoginUser` + Body → `updateMyProfile(userId, command)` → `MyProfileResponse`) KEEP. 다음 항목 FIX:

| 변경점 | 근거 |
|---|---|
| §1 Body → **`UpdateMyProfileRequest` 4 필드 화이트리스트** (displayName / bio / avatarUrl / locale, 모두 nullable) | identity 필드 (email/providers) 는 user/auth 도메인 책임 — 화이트리스트로 격리 |
| §2 응답 스키마 → **`MyProfileResponse` 8 필드** (`getMyProfile` 와 동일) | PATCH 후 클라이언트 즉시 갱신 사용 |
| §3 `profileFacade.updateMyProfile` → **`UpdateMyProfileService.update`** | DDD layered |
| §5 "닉네임 중복 → 409" → **DROP** (`displayName` 고유성 미적용) | Discord/Telegram 패턴, 식별은 id |
| §5 신규 `400 DISPLAY_NAME_CHANGE_TOO_FREQUENT` 추가 | 7일 1회 빈도 제한 |
| §6 PATCH 의미 → **null = 변경 없음, 빈 문자열은 displayName 한정 400** | 명시적 정의 |

**후속 작업 (Phase 6-b 이후 코드 슬라이스, 본 PR 범위 외)**:
- VO 신설: `Bio` (0-200) / `AvatarUrl` (URL ≤500) / `Locale` (enum-like). `DisplayName` VO 는 **existing** (User aggregate 가 이미 record 로 보유, MAX_LENGTH=32 — 재작성 X)
- Port: `DisplayNameChangeRateLimiter` (User aggregate 측, Redis SETEX adapter, key `user:displayName_changed:{userId}`)
- Application: `UpdateMyProfileCommand` / `UpdateMyProfileService` (`@Transactional` cross-aggregate — `User.rename` + `Profile.update` 동시, commit 후 retrieve 재호출로 응답 합성)
- Domain (User): **`User.rename(DisplayName, Instant)` 메서드 existing** (`User.java:93`, 재작성 X). 본 슬라이스는 호출만.
- Domain (Profile): `Profile` aggregate 신규 (shared identifier = UserId, displayName 미보유, `update(bio, avatarUrl, locale, now)` 메서드)
- Infrastructure: `RedisDisplayNameChangeRateLimiterAdapter` + `ProfileJpaEntity` + `ProfileMapper` + Flyway V4 (`profiles` 테이블, `display_name` 컬럼 미생성)
- Presentation: `ProfileController.updateMyProfile` + `UpdateMyProfileRequest` (`@Valid` + 화이트리스트 4 필드) + `ProfileErrorCode` 4종 + `ProfileExceptionHandler` 매핑 + `@SecurityRequirement(name = "BearerJwt")`
- WebMvcTest: 화이트리스트 외 필드 무시, 빈도 제한 발동, null 변경 없음, 빈 문자열 의미 검증 (4 필드)
- E2E: 본인 PATCH 후 응답 8 필드 + Redis flag 설정 + 7일 내 재시도 400 + User+Profile 양쪽 변경 검증

**Non-Goals**:
- avatarUrl 의 파일 업로드 (별도 endpoint `POST /me/avatar`)
- locale 화이트리스트 확장 (운영 결정)
- VIP 빈도 제한 면제 (운영 결정)
- email 변경 (LOCAL 한정 별도 endpoint)
- OAuth 연결/해제 (auth 도메인 별도 endpoint)

**구현 PR**: 추후 (Phase 6-b 슬라이스)
