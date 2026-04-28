---
api_id: profile.getMyProfile
http_method: GET
path: /api/v1/profiles/me
auth: Y
controller: ProfileApiController.kt
handler: getMyProfile
status: mined
---

# GET /api/v1/profiles/me — 내 프로필 조회

## 1. 요청 (Request)
- Header: `@LoginUser` (인증된 사용자 본인 조회)

## 2. 응답 (Response)
- 성공: `200 OK` + `MyProfileResponse` (private + identity 5종 흡수, 8 필드)

```json
{
  "id": "uuid",
  "email": "string?",                  // OAuth 가입자는 IdP 미공개 시 null. LOCAL 가입자는 가입 email
  "displayName": "string",
  "providers": ["GOOGLE", "KAKAO"],    // List<String>. OAuth 한정 - LOCAL 가입자는 빈 배열 []
  "createdAt": "2026-04-28T10:00:00Z", // Instant (가입 시각)
  "bio": "string?",                    // 0-200자
  "avatarUrl": "string?",              // URL or null
  "locale": "ko"                        // ISO 639-1 (default "ko")
}
```

- 본인 조회이므로 `email` / `providers` / `createdAt` / `locale` 모두 **포함**.
- `getProfile` (public 조회) 와 응답 DTO **분리** (`PublicProfileResponse` 4 필드).

## 3. 비즈니스 로직 (요약)
1. `RetrieveMyProfileService.retrieve(userId)` → `User` aggregate (id / email / displayName / providers / createdAt) + `Profile` aggregate (bio / avatarUrl / locale) 조회 후 합쳐 반환.

## 4. 데이터 의존
- DB read: `users` (identity 필드) + `profiles` (외형 필드)

## 5. 예외 케이스
- 인증 실패 → 401 (`@LoginUser` resolver 가 처리)
- 사용자 데이터 부재 (정상 흐름에서는 발생 X — User 가 있으면 Profile 도 가입 시점에 생성) → 500 (서버 invariant 위반)

## 6. 암묵적 로직 (Implicit)
- 본인 조회이므로 **private 필드 모두 포함** (`getProfile` 와 다름) — [identity/profile-prd-evaluation](../../decisions/identity/profile-prd-evaluation.md) §1 박제.
- LOCAL 가입자의 `providers: []` (빈 배열) 는 [identity/local-credential-modeling](../../decisions/identity/local-credential-modeling.md) 의 `account_type=LOCAL` + OAuthIdentity 미보유 invariant 의 자연스러운 귀결.
- `email` nullable 은 OAuth 가입자가 IdP 동의 시 미공개 가능성 — ADR-0006 의 email 정책 (nullable + 비-UNIQUE) 정합.

## 7. 호출자 (Clients)
- 모바일 / 웹 (마이페이지 진입 시 단일 호출 — `user.getMyInfo` DROP 으로 `/me` 단일화)

## 8. TODO / Open Questions
- ~~private vs public 필드 분리~~ → 본 endpoint = private 모두 포함, `getProfile` = public-safe 4 필드 (해소)
- ~~identity 필드 5종 흡수~~ → §2 응답 스키마에 명시 (해소)

## 9. KEEP/DROP/FIX 분류

**판정: KEEP (with FIX)** — 2026-04-28, [identity/profile-prd-evaluation](../../decisions/identity/profile-prd-evaluation.md) 와 함께 결정

PRD 의 핵심 흐름 (`@LoginUser` → `retrieveMyProfile(userId)` → `MyProfileResponse`) KEEP. 다음 항목 FIX:

| 변경점 | 근거 |
|---|---|
| §2 응답 스키마 → **8 필드 확정** (id / email / displayName / providers / createdAt / bio / avatarUrl / locale) | [user-profile-domain-boundary](../../decisions/identity/user-profile-domain-boundary.md) 가 identity 5종 흡수 강제. 외형 3종 (bio / avatarUrl / locale) 추가 |
| §3 `profileFacade.retrieveMyProfile` → **`RetrieveMyProfileService.retrieve`** (Application 계층, facade 패턴 미사용) | DDD layered 구조 + CLAUDE.md 핵심원칙 #2 (Application Service 가 진입점) |
| §6 "private 필드 포함 정책" → **본인 조회이므로 모두 포함**, `getProfile` 와 응답 DTO 분리 | 책임 분리 |
| §1 / §2 응답 DTO 명시 — `MyProfileResponse` (위 §2 JSON) | PRD 본문에서 모호했던 응답 필드 확정 |

**후속 작업 (Phase 6-b 이후 코드 슬라이스, 본 PR 범위 외)**:
- `Profile` aggregate 도입 + Flyway V4 (`profiles` 테이블)
- VO 신설: `Bio` (0-200) / `AvatarUrl` (URL ≤500) / `Locale` (enum-like)
- `RetrieveMyProfileService` (application) + `ProfileController.getMyProfile` (presentation) + `MyProfileResponse` DTO
- E2E: 본인 조회 시 8 필드 모두 응답에 포함됨 검증

**구현 PR**: 추후 (Phase 6-b: domain → application → infra → presentation 슬라이스)
