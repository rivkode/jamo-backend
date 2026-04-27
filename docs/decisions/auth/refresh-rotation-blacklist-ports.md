# Decision: Refresh 회전 + 세션 Blacklist 도메인 Port 설계

- **상태**: Accepted
- **결정일**: 2026-04-27
- **결정자**: jonghun
- **관련 ADR**: [ADR-0001 인증 아키텍처](../../adr/0001-authentication-architecture.md)
- **관련 PRD**: [`auth/refresh.md`](../../prd/auth/refresh.md), [`auth/logout.md`](../../prd/auth/logout.md)
- **PR**: PR4-a (`feature/identity-auth-refresh-logout-domain`)

## 컨텍스트

PRD `auth/refresh.md` §9 와 `auth/logout.md` §9 의 FIX 정책으로 다음을 도메인에 표현해야 한다:

1. **회전형 refresh** — 매 `/auth/refresh` 호출 시 신규 sessionId + 신규 refresh JWT 발급, 구 sid 즉시 폐기.
2. **reuse detection** — 폐기된 sid 의 refresh 가 재사용되면 해당 user 의 모든 sid 일괄 폐기 (탈취 의심 보상 트랜잭션).
3. **단일 디바이스 logout** — 현재 sid 만 `bl:sid:{sid}` SET 등록, access JWT 의 sid claim 으로 즉시 거부.

PR4 는 a/b/c 분할(Domain → Application+Infrastructure → Presentation+E2E) 로 진행하며, 본 문서는 PR4-a 의 도메인 모델/Port 설계 결정을 기록한다.

## 결정

### D1. 신규 Port 2개 도입

| Port | 위치 | 시그니처 | 책임 |
|---|---|---|---|
| `SessionBlacklist` | `domain/repository/` | `void blacklist(String sid, Duration ttl)` / `boolean contains(String sid)` | sid 단위 blacklist. logout/reuse detection 양쪽에서 사용. common-auth-jwt 의 `BlacklistChecker` 어댑터가 `contains` 를 호출 (ADR-0001 세부 정책 표) |
| `SessionIdGenerator` | `domain/service/` | `String newSessionId()` | OAuth callback 신규 세션 + refresh 회전 양쪽에서 sid 발급을 통일. 기존 `OAuthCallbackService` 의 inline `UUID.randomUUID()` 를 PR4-b 에서 본 port 로 교체 |

### D2. `RefreshTokenStore` 확장 — `Set<String> findAllSessionIds(UserId)`

reuse detection 시 user 의 모든 sid 를 blacklist 등록 + 일괄 삭제하기 위한 보조 인덱스 조회 메서드. PR4-b 에서 `RefreshTokenRedisStore` 가 `refresh:user:{userId}` Redis SET 으로 정식 구현하고, PR4-a 시점에는 stub override (`UnsupportedOperationException`) 로 둔다.

### D3. RefreshToken 라이프사이클 예외 3종 분리

- `RefreshTokenException` (base, RuntimeException 직속, OAuth 흐름의 `OAuthAuthenticationException` 와 별도 계층)
- `RefreshTokenInvalidException` — 위조 / hash mismatch / 이미 폐기된 sid
- `RefreshTokenExpiredException` — exp 만료 (PRD `REFRESH_EXPIRED` ErrorCode 분기)
- `RefreshTokenReuseDetectedException` — 폐기된 refresh 의 재사용 (보상 트랜잭션 트리거)

### D4. `SessionBlacklist` 만 `Duration ttl` 사용 (다른 모델은 `Instant expiresAt`)

`OAuthFlowSession`, `AuthorizationCode`, `RefreshTokenRecord` 는 `Instant expiresAt` 으로 라이프사이클을 record 안에 포함한다. `SessionBlacklist` 는 보관할 record VO 가 없는 외부 효과 port 라 Redis 의 자연스러운 TTL 표현인 `Duration` 이 적합하다. 호출자(application service) 가 access JWT 잔여 수명 이상의 TTL 을 산정해 전달한다.

### D6. 회전 시 store 순서 + best-effort 정합성 (PR4-b 추가)

`AuthRefreshService.refresh` 의 회전은 (1) 신규 record store → (2) 구 sid blacklist → (3) 구 record delete 순서로 실행되며 **atomic 하지 않다** (Redis Lua/MULTI 미사용). 단계 사이 부분 실패 영향:

| 실패 단계 | 영향 | 정합성 평가 |
|---|---|---|
| (1) store(new) 실패 | 신규 발급 자체 실패. 클라이언트 동일 refresh 로 idempotent 재시도 가능 | 안전 |
| (2) blacklist(old) 실패 | 클라이언트는 신규 페어 수신. 구 access JWT 가 만료까지 사용 가능 (보안 boundary 약화 윈도우 = accessTtl) | **약점** — 운영 모니터링 + 메트릭으로 가시화 필요 |
| (3) delete(old) 실패 | 구 record 잔존. (2) 가 성공이면 blacklist 로 거부. record 는 자동 만료 | 안전 |

검토한 옵션:
- (A) **현행: store→blacklist→delete (best-effort)** — 채택. 부분 실패 시 사용자 락아웃 회피.
- (B) delete→blacklist→store — 거부. delete 성공 + store 실패 시 클라이언트 락아웃.
- (C) Redis Lua/MULTI atomic — 거부. 본 PR scope 초과. 향후 (2) 의 약점이 운영상 문제가 되면 재검토.

`blacklistTtl = accessTtl + clockSkew` 로 산정 (`JwtProperties.blacklistTtl()`). `RsaJwtVerifier` 가 exp+clockSkew 까지 토큰을 valid 로 보므로 blacklist 도 동일 윈도우를 커버해 logout/회전 직후 clockSkew 안의 access JWT 가 통과되는 약점을 차단.

reuse detection 보상도 best-effort — sid 별 실패는 `log.error` + 카운트, **모든 sid 가 실패하면 `IllegalStateException`** 으로 즉시 가시화 (PR4-c 의 ExceptionHandler 가 5xx 매핑). 별도 PR 에서 Micrometer 카운터 + alarm 도입.

### D5. `SessionId` Value Object 격상 보류

`RefreshTokenRecord`, `AuthorizationCode`, `OAuthFlowSession`, `JwtClaims` (common-auth-jwt) 모두 sid 를 `String` 으로 보유한다. PR4 에서 VO 로 격상하면 cross-cutting 변경 폭이 매우 크다 (common-auth-jwt 까지 영향). 본 PR 의 핵심 목표(refresh 회전 + logout) 와 무관한 타입 시스템 정리이므로 별도 PR 로 분리.

## 검토한 옵션 및 근거

### O1. `findAllSessionIds` port 추가 vs SCAN 패턴

| 옵션 | 장점 | 단점 |
|---|---|---|
| **A. port 추가 + Redis 보조 인덱스 (`refresh:user:{userId}` SET)** | O(1) sid 수집, 운영 안전 | store/delete 시점에 SADD/SREM 동기 필요 (PR4-b 부담) |
| B. 인터페이스 미변경, 어댑터에서 SCAN | port 단순 | SCAN 은 운영 hot path 에 부적절 — Redis 권고와 충돌 |

→ **A 채택**. reuse detection 은 보안 보상 트랜잭션 hot path 라 SCAN 회피.

### O2. PR4-a 시점 미구현 메서드 표현

| 옵션 | 장점 | 단점 |
|---|---|---|
| A. 인터페이스 default + `throw UnsupportedOperationException` | 어댑터 수정 불필요, 머지 단순 | default 의 표준 의미("안전한 기본 동작") 와 신호 충돌 |
| **B. abstract 시그니처 + 어댑터에서 stub override** | 인터페이스 의도 명확, 미구현 격리가 어댑터 한 곳 | 어댑터 한 줄 추가 |

→ **B 채택** (code-reviewer M1 반영). PR4-b 에서 stub override 한 줄을 정식 구현으로 교체.

### O3. RefreshToken 예외 분리 vs 단일

| 옵션 | 장점 | 단점 |
|---|---|---|
| **A. 3종 분리(Invalid/Expired/Reuse) + 공통 base** | PRD §9 의 ErrorCode 분기와 1:1, security 메트릭 분리 | 클래스 4개 |
| B. 단일 `RefreshTokenException` + ErrorCode enum | 클래스 적음 | 보상 트랜잭션 트리거(reuse) 와 일반 invalid 가 한 클래스에 섞여 ExceptionHandler 분기 복잡 |

→ **A 채택**. PR3 의 OAuth 예외 계층(`OAuthAuthenticationException` + sub) 패턴과 일관.

### O4. `SessionBlacklist` 시그니처 — `Duration ttl` vs `Instant expiresAt`

| 옵션 | 장점 | 단점 |
|---|---|---|
| **A. `Duration ttl`** | Redis SET EX 와 직결, 보일러플레이트 없음 | 다른 도메인 모델과 시간 표현 비대칭 |
| B. `Instant expiresAt` | 다른 record VO 와 일관 | 어댑터마다 `Duration.between(clock.instant(), expiresAt)` 환산 강제 |

→ **A 채택**. `SessionBlacklist` 는 보관 VO 가 없는 외부 효과 port — record VO 와 동일 일관성을 강제할 이유 없음. JavaDoc 에 사유 명시.

## 결과 및 영향

- **PR4-a 산출물**: 6개 신규 (예외 4 + port 2) + 2개 수정 (`RefreshTokenStore`, `RefreshTokenRedisStore` stub).
- **PR4-b 작업 항목**:
  - `SessionBlacklistRedisStore` (`bl:sid:{sid}` SET, TTL 적용)
  - `UuidSessionIdGenerator`
  - `RefreshTokenRedisStore` 의 `refresh:user:{userId}` 보조 인덱스 SADD/SREM, `findAllSessionIds` 정식 구현
  - `AuthRefreshService` (회전 + reuse detection 보상)
  - `AuthLogoutService` (현재 sid blacklist 등록 + delete)
  - `OAuthCallbackService` 의 inline UUID → `SessionIdGenerator` 로 교체
  - `BlacklistChecker = SessionBlacklist::contains` 어댑터 빈 wiring
- **PR4-c 작업 항목**:
  - `AuthRefreshController` / `AuthLogoutController`, `@LoginUser` ArgumentResolver
  - `AuthErrorCode.REFRESH_EXPIRED` / `REFRESH_INVALID` / `REFRESH_REUSE_DETECTED` 추가
  - E2E (Testcontainers + WireMock — 회전 happy + reuse detection + logout 후 access 거부)
- **별도 정리 PR**: `SessionId` VO 격상 (cross-module), `AuthorizationCodeGenerator` 위치 정리 (`domain/model/auth` → `domain/service`).

## 관련

- 코드 리뷰 결과: PR4-a `code-reviewer` APPROVE WITH COMMENTS — M1(stub override), M2(TTL 책임 JavaDoc), L1(Duration 사유) 반영 완료.
- ADR-0001 — sid blacklist 정책의 상위 결정.
