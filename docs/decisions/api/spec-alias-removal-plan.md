# API 명세 정합 alias — 도입 / 제거 정책

- **상태**: Accepted
- **날짜**: 2026-05-27
- **영향 범위**: identity-service, diary-service (presentation 계층)
- **관련 plan**: `/Users/jonghun/.claude/plans/docs-prd-0526-flutter-md-vivid-lovelace.md` §4 Slice 2

## 컨텍스트

프론트엔드 PRD `docs/prd/0526_flutter.md` (Flutter 웹) 의 API 명세가 백엔드 내부 PRD 들과 path / field 명에서 차이가 있다. frontend agent 변경을 최소화하기 위해 백엔드에 **양방향 호환 alias** 를 도입했다 (Slice 2). alias 는 일시적 호환 layer 이며 영구 유지 시 다음 문제가 발생한다:

- OpenAPI 응답 schema bloat (필드 수 ×1.5~2)
- contract drift (두 필드가 분기될 위험 — 예: `author.username` 만 갱신되고 `authorDisplayName` 누락)
- 신규 클라이언트가 어떤 필드를 SoT 로 읽어야 하는지 혼동
- single-OAuth 가정 (`provider` 단수 alias) 이 multi-account-linking 도입 시 정보 손실

## 결정

### 1. 도입 범위 (Slice 2 머지 시점 확정)

| Domain | DTO 또는 path | 정식 필드 (SoT) | alias 필드 | 입출력 |
|---|---|---|---|---|
| identity | `RegisterUserRequest` | `displayName` | `username` (`@JsonAlias`) | 입력 |
| identity | `RegisterUserResponse` | `displayName` | `username` | 출력 |
| identity | `MyProfileResponse` | `displayName`, `providers` (배열) | `username`, `provider` (단수, providers[0]) | 출력 |
| identity | `PublicProfileResponse` | `displayName` | `username` | 출력 |
| identity | `AuthExchangeResponse` | `expiresInSeconds` | `expiresIn`, `tokenType` ("Bearer" 고정) | 출력 |
| identity | path | `/api/v1/auth/login`, `/api/v1/profiles/me`, `/api/v1/profiles/{id}` | `/api/v1/users/login`, `/api/v1/users/me`, `/api/v1/users/{id}` | UserPathAliasController 위임 |
| diary | `DiaryResponse` | `visibility`, `likedByMe`, `authorId`, `authorDisplayName` | `isPublic`, `userLiked`, `author{userId,username,avatarUrl}` | 출력 |
| diary | `ToggleDiaryLikeResponse` | `liked` | `userLiked` | 출력 |
| diary | sort query | `popular` | `trending` (Application 단계 매핑) | 입력 |

### 2. SoT (Single Source of Truth) 선언

신규 클라이언트는 **정식 필드** (위 표 "정식 필드 (SoT)" 열) 를 사용해야 한다. alias 필드는 frontend 의 점진적 전환 기간을 위한 일시적 호환 노출이다.

### 3. 제거 트리거

다음 두 조건이 동시에 충족되면 alias 를 PR 단위로 제거한다:

- **(C1)** frontend 가 정식 필드 사용으로 전환 완료 (frontend agent 확인).
- **(C2)** 본 백엔드 PRD 도 정식 필드를 명세로 채택 (`docs/prd/0526_flutter.md` 갱신 + alias 명세 표기 제거).

C1 / C2 가 동시 충족되지 않은 alias 는 유지한다. C2 가 단독 충족 시 — frontend 가 정식 필드 사용 가능하더라도 alias 호출 중인 클라이언트 잔존 가능성 — alias 유지.

### 4. 제거 절차

alias 제거 PR 은 다음 순서를 따른다:

1. **deprecation 단계** (선행 1 sprint 권장): alias getter / `@JsonAlias` 에 `@Deprecated(forRemoval = true)` + Javadoc `@deprecated` 추가. OpenAPI 응답 schema 에 `deprecated: true` 자동 노출.
2. **모니터링**: 1 sprint 동안 access log 에서 alias path 호출 (예: `/api/v1/users/login`) 또는 응답 필드 사용 측정 (가능 시).
3. **제거**: alias getter / 어노테이션 / path alias controller / sort 매핑 코드 제거. 본 문서 § 1 표에서 해당 행 strikethrough + 제거 PR 링크 추가.
4. **PRD 갱신**: `docs/prd/0526_flutter.md` 의 §1~§2 표에서 alias 표기 제거.

### 5. 신규 alias 추가 정책

본 plan 머지 시점 (2026-05-27) 이후 신규 PRD 호환 alias 는 **본 문서 § 1 표 갱신 + 박제 PR 필수**. javadoc 만 박제하고 본 인덱스 미갱신은 금지.

### 6. 단수 `provider` alias 의 multi-OAuth 위험 (특별 항목)

`MyProfileResponse.provider` 는 `providers[0]` 만 노출. 사용자가 GOOGLE + KAKAO 둘 다 연동한 경우 frontend 가 `provider` 만 읽으면 KAKAO 연동 사실을 못 본다. 백엔드는 multi-account-linking 도입 시 본 alias 의 정보 손실 위험이 커진다 — 그 시점에는 **C1 (frontend 전환) 완료 전이라도** alias 를 **deprecation 단계로 즉시 진입**한다. 박제: `MyProfileResponse.java` javadoc cross-reference.

## 근거

- frontend 변경 비용 vs 백엔드 alias 추가 비용: 후자가 작음 (현 시점 결정).
- alias 영구 유지는 OpenAPI schema bloat / contract drift 누적이 명확한 단점.
- Deprecation 단계를 두면 access log 모니터링 후 안전 제거 가능.

## 결과

- alias 필드는 SoT 가 아니다 — 본 문서 § 2 명시.
- frontend 가 정식 필드로 전환 후 `feat(api): alias 제거` 또는 그에 상응하는 PR 로 단계적 제거.
- multi-account-linking 도입 시 `provider` 단수 alias 우선 deprecation (special-case § 6).
- 본 문서는 alias 도입 시점 (Slice 2) 이후 alias 가 추가/제거될 때마다 갱신.
