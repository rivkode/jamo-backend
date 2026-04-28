---
api_id: profile.getProfile
http_method: GET
path: /api/v1/profiles/{userId}
auth: Y
controller: ProfileApiController.kt
handler: getProfile
status: mined
---

# GET /api/v1/profiles/{userId} — 타 사용자 프로필 조회

## 1. 요청 (Request)
- Header: `@LoginUser` (loginUserId — 인증 필요. 비로그인 조회 X)
- Path: `userId: UUID` (`@NotNull`)

> 타입 변경 — 기존 `Long` 은 legacy 패턴. 본 백엔드는 `UserId = UUID` 로 통일 ([ADR-0006](../../adr/0006-oauth-provider-integration.md), [identity/local-credential-modeling](../../decisions/identity/local-credential-modeling.md)).

## 2. 응답 (Response)
- 성공: `200 OK` + `PublicProfileResponse` (public-safe, 4 필드)

```json
{
  "id": "uuid",
  "displayName": "string",
  "bio": "string?",        // 0-200자
  "avatarUrl": "string?"   // URL or null
}
```

- **노출 X**: `email` / `providers` / `createdAt` / `locale` — [`UserSummaryService` public-safe 정책](../../decisions/identity/profile-prd-evaluation.md) 정합 (식별 가능 정보의 cross-user 노출 차단).
- viewer-context (follow 여부) **미포함** — follow 도메인 부재로 Non-Goal.

## 3. 비즈니스 로직 (요약)
1. `RetrieveProfileService.retrieve(loginUserId, userId)` → `User` aggregate 의 `displayName` + `Profile` aggregate 의 `bio`, `avatarUrl` 조회 후 4 필드만 응답.
2. `loginUserId` 는 인증 검증 외 응답에 영향 없음 (viewer-context 미적용).

## 4. 데이터 의존
- DB read: `users` (`displayName`) + `profiles` (`bio`, `avatar_url`)

## 5. 예외 케이스
- 인증 실패 → 401 (`@LoginUser` resolver)
- 사용자 없음 → 404 `USER_NOT_FOUND`
- `userId` validation 실패 (UUID 형식 아님) → 400

## 6. 암묵적 로직 (Implicit)
- `loginUserId` 는 **응답에 viewer-context 미포함** — follow 도메인 도입 시 `isFollowing` / `isFollowedBy` 추가 예정 ([identity/profile-prd-evaluation](../../decisions/identity/profile-prd-evaluation.md) §2 Non-Goal).
- **비공개 프로필 차단 미적용** — 모든 프로필이 공개. follow 도메인 도입 시 정책 결정.
- `getMyProfile` (private, 8 필드) 와 응답 DTO **분리** (`PublicProfileResponse` 4 필드).

## 7. 호출자 (Clients)
- 모바일 / 웹 (타 사용자 프로필 화면)

## 8. TODO / Open Questions
- ~~비공개 프로필 차단 정책~~ → follow 도메인 미존재로 Non-Goal, 후속 결정 (해소: 후속)
- ~~viewer-context 포함 여부~~ → follow 도메인 미존재로 미포함 (해소)
- ~~email/providers/createdAt 등 식별 필드 public 노출 여부~~ → 미노출 (UserSummary 정합) (해소)

## 9. KEEP/DROP/FIX 분류

**판정: KEEP (with FIX)** — 2026-04-28, [identity/profile-prd-evaluation](../../decisions/identity/profile-prd-evaluation.md) 와 함께 결정

PRD 의 핵심 흐름 (`@LoginUser` + `userId` → `retrieveProfile(loginUserId, userId)` → `PublicProfileResponse`) KEEP. 다음 항목 FIX:

| 변경점 | 근거 |
|---|---|
| §1 path `userId: Long` → **`userId: UUID`** | 본 백엔드 식별자는 UUID 통일 (legacy `Long` 불일치) |
| §2 응답 스키마 → **`PublicProfileResponse` 4 필드** (id / displayName / bio / avatarUrl) | [`UserSummaryService` public-safe 정책](../../decisions/identity/profile-prd-evaluation.md) 정합 (PII cross-user 노출 차단). HTTP 는 외형 (bio/avatarUrl) 추가만 |
| §3 `profileFacade.retrieveProfile` → **`RetrieveProfileService.retrieve`** | DDD layered 구조 |
| §6 "viewer-context (팔로우 여부)" → **미포함**, follow 도메인 도입 시 후속 | follow 도메인 부재 |
| §6 "비공개 프로필 차단" → **미적용**, follow 도메인 도입 시 후속 | 동일 |
| §5 "0 이하 userId → 400" → **UUID 형식 validation 400** | path 타입 변경에 따른 정합 |

**Non-Goals (본 endpoint 의 후속 결정)**:
- follow 도메인 도입 시 `isFollowing` / `isFollowedBy` 추가
- 비공개 프로필 차단 정책 (e.g., 비공개 = 팔로워만 조회)
- `displayName` 검색 endpoint (`GET /api/v1/profiles?q=...`) — 별도 PRD

**후속 작업 (Phase 6-b 이후 코드 슬라이스, 본 PR 범위 외)**:
- `RetrieveProfileService` (application) + `ProfileController.getProfile` (presentation) + `PublicProfileResponse` DTO
- `ProfileErrorCode.USER_NOT_FOUND` (404) + `ProfileExceptionHandler` 매핑
- WebMvcTest: 4 필드만 응답에 포함됨 검증 (private 필드 누락 검증)

**구현 PR**: 추후 (Phase 6-b 슬라이스)
