# Decision: User vs Profile 도메인 경계 — `/me` 조회 단일화

- **상태**: Accepted
- **결정일**: 2026-04-27
- **결정자**: jonghun
- **PR**: `feature/identity-user-prd-evaluation` (user 도메인 PRD 평가 PR)
- **관련 ADR**: [ADR-0001 인증 아키텍처](../../adr/0001-authentication-architecture.md), [ADR-0006 OAuth Provider 통합](../../adr/0006-oauth-provider-integration.md)
- **관련 PRD**: [`prd/user/getMyInfo.md`](../../prd/user/getMyInfo.md), [`prd/profile/getMyProfile.md`](../../prd/profile/getMyProfile.md)

## 컨텍스트

identity-service 안에는 두 도메인이 공존한다.

- **user**: 가입 / 이메일 검증 / 비밀번호 등 *계정 식별·자격증명* 책임.
- **profile**: 표시 정보 / 외형 / 사회적 정보 (avatar, bio, locale, savedClips 등) 책임.

PRD 미네이션 시점 (legacy 추출) 에 user 도메인에 `getMyInfo` (`GET /api/v1/users/me`), profile 도메인에 `getMyProfile` (`GET /api/v1/profiles/me`) 가 각각 존재했고, 두 PRD 모두 §6/§8 에서 책임 경계 모호함을 Open Question 으로 남겼다 (`UserInfo` 와 `ProfileResponse` 의 중복 정보, private vs public 분리 등).

본 결정 시점에 user 도메인 PRD 4개를 일괄 평가하면서 두 endpoint 의 존재 가치를 재검토했다.

## 검토한 옵션

| 옵션 | 설명 | RTT | 도메인 책임 |
|---|---|---|---|
| **A. 둘 다 유지 (분리)** | `getMyInfo` = identity 필드만, `getMyProfile` = 외형 필드만 | 마이페이지 진입 2회 호출 | 명확하지만 클라이언트 fan-out 부담 |
| **B. `getMyInfo` DROP, `getMyProfile` 단일화** | profile endpoint 가 identity + profile 필드 모두 반환 | 1회 호출 | profile 가 identity 필드를 노출 (read 책임만 흡수) |
| **C. `getMyProfile` DROP, `getMyInfo` 단일화** | user endpoint 가 외형 필드까지 반환 | 1회 호출 | user 도메인이 외형 책임을 흡수 — 가입/검증과 책임 충돌 |

### 결정 — 옵션 **B**: `user.getMyInfo` DROP, `profile.getMyProfile` 단일화

`getMyInfo` 를 DROP 한다. `/me` 조회 흐름은 `profile.getMyProfile` 단일 endpoint 로 통합하고, profile 응답에 identity 필드(id / email / displayName / providers / createdAt) 를 흡수한다.

## 근거

1. **클라이언트 호출 단순화** — 마이페이지 진입 시 단일 endpoint 호출로 RTT 1회 절감. 모바일 앱처럼 RTT 비용이 큰 환경에서 유리.
2. **도메인 책임 분리 vs read 통합** — read 는 *조회* 라는 단일 행위 책임이므로 한 곳에 모으는 것이 ROI 가 높다. 가입/검증 (write 책임) 은 user 도메인에 그대로 격리되므로 책임 분리 원칙 자체는 깨지지 않는다.
3. **OAuth-only 현 단계의 적합성** — 현재 LOCAL signup 미구현, 모든 사용자는 OAuth 가입자. identity 필드는 사실상 *프로필의 일부* 로 노출되어도 무방한 정보 (email/displayName/createdAt/providers).
4. **두 PRD 의 Open Question 동시 해결** — `getMyInfo §8 "User vs Profile 경계"` 와 `getMyProfile §6 "private vs public"` 두 질문이 한 번의 결정으로 해소.
5. **옵션 C 거부 사유** — user 도메인이 외형 필드를 가지면 profile 도메인의 존재 의의가 사라진다. user 도메인은 가입/검증의 단일 책임을 유지해야 인증 흐름이 깔끔.

## 결과 및 영향

### `user` 도메인
- `getMyInfo.md` PRD 는 **DROP** 으로 §9 에 박제.
- user 도메인의 남은 책임 = 회원가입 (`createUser`) + 이메일 검증 (`sendValidationNumber`, `validateEmail`).
- user 도메인은 **read endpoint 를 보유하지 않음** (write-only domain). 식별·자격증명에만 집중.

### `profile` 도메인
- `getMyProfile.md` 응답 스키마에 identity 필드 5종 추가 명시 필요:
  - `id` (UserId, UUID)
  - `email` (String, nullable — OAuth 제공자가 미공개 시)
  - `displayName` (String)
  - `providers` (List<String> — OAuthIdentity 의 provider 목록, 예: `["GOOGLE", "KAKAO"]`)
  - `createdAt` (Instant)
- `private vs public 필드 분리` 도 함께 결정 필요 — 본 endpoint 는 본인 조회 (`@LoginUser`) 이므로 private 필드 포함 OK. public 조회 (`getProfile`) 와는 응답 DTO 분리 (이미 PRD 에서 별도 endpoint).
- profile 도메인 평가 PR 에서 §1 / §2 / §9 함께 갱신.

### 트래커
- `_status.md`: user 행 `KEEP 0 / FIX 3 / DROP 1` (createUser, sendValidationNumber, validateEmail 은 FIX, getMyInfo 는 DROP).
- profile 행은 변경 없음 (평가 미진행 상태). 다만 본 결정으로 후속 평가 시 응답 스키마 추가가 사실상 강제.

### Non-Goals (본 결정에서 다루지 않음)
- profile 도메인의 응답 DTO 명세 자체 (필드 확정은 profile PRD 평가 PR 에서)
- public profile (`profile.getProfile`) 의 외부 노출 정책
- savedClips 등 collection 필드의 페이지네이션 / lazy 로드

## 참고

- [`docs/prd/_status.md`](../../prd/_status.md) §평가 절차
- [ADR-0006](../../adr/0006-oauth-provider-integration.md) — User aggregate 의 email 정책 (nullable, 비-UNIQUE)
- 일반 패턴: Twitter / Facebook 의 `/me` API 가 identity + profile 통합 응답
