# Decision: Auth Presentation 응답 ErrorCode + 인증 메커니즘 정책

- **상태**: Accepted
- **결정일**: 2026-04-27
- **결정자**: jonghun
- **관련 ADR**: [ADR-0001 인증 아키텍처](../../adr/0001-authentication-architecture.md)
- **관련 PRD**: [`auth/refresh.md`](../../prd/auth/refresh.md), [`auth/logout.md`](../../prd/auth/logout.md)
- **PR**: PR4-c (`feature/identity-auth-refresh-logout-presentation`)

## 컨텍스트

PR4-c 는 `POST /api/v1/auth/refresh` + `POST /api/v1/auth/logout` 의 HTTP 표면을 도입한다. PRD §9 의 ErrorCode (`REFRESH_EXPIRED`) 와 새로 등장하는 인증 실패 응답 처리, `@LoginUser` 인증 메커니즘 도입 방식에 대한 결정 박제.

## 결정

### D1. REUSE_DETECTED 응답은 INVALID 로 통합

`RefreshTokenReuseDetectedException` 도 응답에서는 `REFRESH_INVALID` (401) 로 매핑. server-side log 에만 reuse 구분 가시화 (이미 PR4-b 의 `triggerReuseCompensation` 이 log + 카운터). 클라이언트는 reuse 와 일반 위조를 응답으로 구분 불가.

근거:
- OWASP authentication error generic 권고 — "감지 신호" 자체가 공격자에게 정보 게인.
- IETF draft-ietf-oauth-browser-based-apps §6 — refresh token 재사용 응답 차이를 두지 말 것.
- 보상 트랜잭션은 application 이 이미 server-side 로 수행 — 클라이언트가 알 필요 없음.

### D2. 인증 실패는 단일 `UNAUTHORIZED`

logout 등 보호 endpoint 에서 (a) Authorization 헤더 부재 / (b) Bearer 미적용 / (c) JWT 만료 / (d) JWT 위조 / (e) sid blacklist / (f) tokenType 불일치 — 모두 401 + `UNAUTHORIZED` 단일 응답.

근거:
- 만료 / 위조 / blacklist 분기를 응답으로 노출하면 공격자에게 token 라이프사이클 oracle 제공.
- SPA 는 access JWT 의 exp 를 사전에 감지해 refresh 호출 시도 — 만료 신호를 server 가 별도 노출할 필요 없음.
- 401 받으면 클라이언트는 일괄 재로그인.

### D3. `@LoginUser` ArgumentResolver 채택 (Spring Security 보류)

protect endpoint 의 인증은 `@LoginUser` 어노테이션 + `LoginUserArgumentResolver` 단일 메커니즘. Spring Security 의존성 추가 / SecurityFilterChain 도입은 별도 PR 로 분리.

근거:
- PR4-c 한정 protect endpoint 1개 (logout) — Spring Security 도입 비용 과잉.
- PR3 의 `DeviceIdResolver` 와 동일 패턴 (`@Component` resolver + `WebMvcConfigurer` 등록) — 구조 일관.
- `LoginUserArgumentResolver` 가 (1) Authorization Bearer → (2) JwtVerifier.verify → (3) tokenType=ACCESS 강제 → (4) `AuthenticatedUser` 주입 단계 캡슐화. 모든 실패는 `UnauthorizedException` 단일 매핑.

### D4. tokenType=ACCESS 강제

`@LoginUser` resolver 는 `JwtClaims.tokenType() != ACCESS` 면 즉시 거부. refresh JWT 로 보호 endpoint 호출 차단.

### D5. AuthRefreshController 응답에 sid raw value / reuse 신호 비노출

`AuthExceptionHandler` 의 `handleRefreshInvalid` 가 generic message ("refresh token is invalid") 만 노출. 도메인 예외 raw message (예: "reuse for sid=xyz") 은 응답에 포함되지 않는다 — `AuthRefreshControllerTest` 의 음성 단언 (`body doesNotContain "REUSE", sid value`) 으로 회귀 방어.

### D6. `handleNotReadable` / `handleValidation` 의 ex 객체 비로깅

Jackson `HttpMessageNotReadableException` / Validation 의 message 는 사용자 입력 일부 (예: malformed JSON 의 raw refresh token) 를 포함할 수 있어 stack trace 전체 로깅 회피. 클래스명만 `log.warn(reason={ClassName})` 로 박제 (CWE-532, security review M4).

## 검토한 옵션 및 근거 (요약)

### O1. REUSE 응답 — 별도 노출 vs 통합

| 옵션 | 결과 |
|---|---|
| A. `REFRESH_INVALID` 통합 ← 채택 | OWASP 권고 정합. 보상은 server-side. |
| B. `REFRESH_REUSE_DETECTED` 별도 | UX 분기 가능하지만 공격자도 동일 oracle 받음 — 거부. |

### O2. 인증 실패 분기 vs 단일

| 옵션 | 결과 |
|---|---|
| A. 단일 `UNAUTHORIZED` ← 채택 | 표준. SPA 의 일괄 재로그인. |
| B. `AUTH_EXPIRED` vs `UNAUTHORIZED` | 의미 차이 작고 정보 노출 — 거부. |

### O3. 인증 메커니즘

| 옵션 | 결과 |
|---|---|
| A. `@LoginUser` ArgumentResolver ← 채택 | 변경 폭 최소. PR4-c scope 적합. |
| B. Spring Security + SecurityFilterChain | 표준이지만 의존성/설정 비용 큼. **별도 PR 로 분리**. |

## 결과 및 영향

- 신규 ErrorCode: `REFRESH_EXPIRED`, `REFRESH_INVALID`, `UNAUTHORIZED`
- `AuthExceptionHandler` 매핑: RefreshTokenExpired→EXPIRED, Invalid+Reuse→INVALID 통합, Unauthorized→UNAUTHORIZED 단일
- `LoginUserArgumentResolver` + `AuthenticatedUser` + `UnauthorizedException` + `LoginUser` annotation
- `PresentationWebConfig` 가 모든 controller 에 cross-cutting 영향 — controller slice test 는 `@MockitoBean JwtVerifier` 추가 필요

## 후속 결정 항목 (별도 PR 예고)

본 PR4-c security review 에서 도출된 항목들:

- [ ] **Security H1 — Spring Security 도입 또는 default-deny 정책 결정** (`docs/decisions/auth/security-filter-architecture.md` 신규 + 별도 PR). opt-in `@LoginUser` 모델은 신규 보호 endpoint 추가 시 누락 위험 — 화이트리스트 + default-deny 검토.
- [ ] **Security H2 — Rate limiting 결정** (`docs/decisions/auth/rate-limiting.md` 신규 + 별도 PR). `/auth/refresh` brute-force 방어 + reuse compensation 비용 증폭 차단. Gateway vs app-level, key (IP / userId), 한도 결정.
- [ ] **Security M1 — Refresh token 전송 방식 결정** (`docs/decisions/auth/refresh-token-transport.md` 신규). Body (현 정책) vs HttpOnly Cookie 의 XSS / CSRF / BFF trade-off 박제.
- [ ] **Security M2 — `AuthLogoutService` 호출자 화이트리스트** ArchUnit 룰 추가 (현재는 docstring 만).
- [ ] **Security M3 — ArgumentResolver / ExceptionHandler 의 cause class 별 logging 보강** (서버 가시성).
- [ ] **Security M5 — deviceId binding 검증 결정** — JWT 의 deviceId claim 검증 도입 또는 claim 제거.
- [ ] **Code M1 — `AuthErrorCode` 그룹화 주석/prefix 통일**.
- [ ] **Code M2 — `AuthExchangeResponse.from(AuthExchangeResult)` 팩토리 도입** (Refresh / Exchange controller 응답 매핑 DRY).
- [ ] **Code M3 — `common-auth-web` 모듈 신설** (다른 서비스에 `@LoginUser` 재사용 시) — 두 번째 protect endpoint 가 등장하는 시점에 ADR 결정.

## 관련

- 코드 리뷰: PR4-c code-reviewer / test-reviewer / security-reviewer 모두 APPROVE WITH COMMENTS — 본 PR 반영 7건 + 별도 PR 9건 deferral 명시.
- 시리즈 선행: PR4-a (#14, domain), PR4-b (#16, application+infrastructure)
