---
api_id: auth.login
http_method: POST
path: /api/v1/auth/login
auth: N
controller: AuthLoginController.java
handler: login
status: proposed
---

# POST /api/v1/auth/login — 로그인 (OAuth + LOCAL)

## 1. 요청 (Request)

### LOCAL email/password 로그인
- Header: `X-Device-Id` optional
- Cookie: `jamo_device_id` optional
- Body: `AuthLoginRequest` (`@Valid`)
  - `email`: String, required, email format, max 254 chars
  - `password`: String, required, 8..72 chars, write-only
- 인증 미요구

```json
{
  "email": "user@example.com",
  "password": "plain-password"
}
```

### OAuth 로그인
- 별도 JSON body 로 provider token 을 직접 받지 않는다.
- 기존 OAuth 브라우저 흐름을 로그인 경로로 사용한다.
  1. `GET /api/v1/auth/oauth/{provider}/start`
  2. `GET /api/v1/auth/oauth/{provider}/callback`
  3. `POST /api/v1/auth/exchange`
- 지원 provider: `KAKAO`, `NAVER`, `GOOGLE`

## 2. 응답 (Response)

### LOCAL 성공
- `200 OK` + exchange/refresh 와 동일한 토큰 응답 DTO

```json
{
  "userId": "uuid",
  "accessToken": "access.jwt",
  "refreshToken": "refresh.jwt",
  "expiresInSeconds": 900
}
```

### OAuth 성공
- `auth/exchange.md` 와 동일: OAuth callback 이 발급한 일회성 authorization code 를
  `POST /api/v1/auth/exchange` 로 교환해 위와 같은 토큰 응답을 받는다.

## 3. 비즈니스 로직 (요약)

### LOCAL email/password 로그인
1. `AuthLoginController.login` 이 request DTO 를 `AuthLoginCommand` 로 변환.
2. `AuthLoginService.login(command)` 호출.
3. `Email` VO 로 이메일 형식 검증.
4. `DeviceIdResolver` 가 `X-Device-Id` → `jamo_device_id` cookie → 신규 `web-{UUID}` 순서로 deviceId 를 결정한다.
5. `LoginRateLimiter.isAllowed(email, clientIp, deviceId)` 로 실패 시도 한도를 확인한다.
6. `UserRepository.findLocalAccountByEmail(email)` 로 LOCAL 계정만 조회.
7. 계정이 없거나 OAuth-only 계정이면 `PasswordEncoder.matchesDummy(rawPassword)` 를 수행한 뒤 동일한 인증 실패 예외를 던진다.
8. `PasswordEncoder.matches(rawPassword, hashedPassword)` 가 실패하면 실패 횟수를 기록하고 동일한 인증 실패 예외를 던진다.
9. 성공 시 로그인 실패 카운터를 reset 하고 신규 `sessionId` 를 발급한다.
10. exchange 와 동일한 방식으로 access JWT + refresh JWT 페어 발급.
11. refresh JWT 는 raw token 이 아닌 hash 만 `RefreshTokenStore` 에 저장.
12. `{ userId, accessToken, refreshToken, expiresInSeconds }` 반환.

### OAuth 로그인
1. `start.md` 의 provider redirect + state / PKCE flowSession 생성.
2. `callback.md` 의 state 검증 + provider token/userinfo 호출 + user find-or-register + authorization code 발급.
3. `exchange.md` 의 authorization code atomic consume + access/refresh JWT 페어 발급.

## 4. 데이터 의존

### LOCAL
- DB read: `users` (`account_type = LOCAL`, `email = ?`)
- Redis read/write: login failure counter (`LoginRateLimiter`), refresh token hash 저장 (`RefreshTokenStore`)
- Redis/session id: 신규 `sid` 발급 후 JWT claim 에 포함
- Cookie/Header: deviceId resolve, 신규 생성 시 `jamo_device_id` cookie 발급
- Kafka: 없음
- 외부 API: 없음

### OAuth
- 외부 API: provider token/userinfo
- DB read/write: provider identity 조회 또는 신규 OAuth user 등록
- Redis: flowSession, authorization code, refresh token hash

## 5. 예외 케이스

### LOCAL
- 요청 body validation 실패 → `400 VALIDATION_FAILED`
- email format invalid → `400 VALIDATION_FAILED`
- 계정 없음 / 비밀번호 불일치 / OAuth-only 계정에 대한 password 로그인 시도 → `401 LOGIN_INVALID`
- 실패 시도 한도 초과 → `429 LOGIN_RATE_LIMITED`
- refresh token 저장 실패 등 예기치 못한 서버 오류 → `500 INTERNAL_ERROR`

### OAuth
- `start.md`, `callback.md`, `exchange.md` 의 예외 정책을 따른다.
- OAuth provider 인증 실패는 JSON login endpoint 에서 처리하지 않는다.

## 6. 암묵적 로직 (Implicit)

- LOCAL 로그인 실패 응답은 계정 존재 여부와 비밀번호 불일치를 구분하지 않는다.
- LOCAL 계정 누락과 비밀번호 불일치의 응답 시간 차이를 줄이기 위해 dummy password verification 을 수행한다.
- OAuth 가입자와 LOCAL 가입자는 `AccountType` 으로 분리되어 있으며, 같은 email 이 있어도 자동 링크하지 않는다.
- LOCAL 로그인은 exchange 와 같은 token issuing 규칙을 사용하지만 authorization code 를 만들거나 소비하지 않는다.
- refresh token 은 refresh/logout 과 같은 회전/폐기 모델을 따른다.
- `deviceId` 는 request body 가 아니라 OAuth start 와 같은 header/cookie 기반 resolver 를 사용한다.

## 7. 호출자 (Clients)

- 프론트엔드 SPA / 모바일
  - LOCAL: `POST /api/v1/auth/login`
  - OAuth: browser redirect 기반 `start → callback → exchange`

## 8. TODO / Open Questions

- [x] LOCAL login deviceId 입력은 OAuth start 와 같은 `X-Device-Id` header / `jamo_device_id` cookie / fallback cookie 발급 정책을 사용
- [x] LOCAL login 실패 rate limit 은 `email+clientIp`, `email+deviceId` 2개 Redis bucket soft guard 로 적용
- [ ] 장기 account lock / progressive delay 정책
- [ ] OAuth + LOCAL 계정 linking 지원 여부

## 9. KEEP/DROP/FIX 분류

**판정: proposed** — 2026-05-01

기존 auth 5개 PRD 는 OAuth 브라우저 로그인과 토큰 교환, refresh/logout 을 이미 다루지만
LOCAL email/password 로그인 진입점이 비어 있다. 본 PRD 는 다음을 보완한다.

| 항목 | 결정 |
|---|---|
| OAuth 로그인 | 기존 `start → callback → exchange` 를 공식 OAuth 로그인 흐름으로 유지 |
| LOCAL 로그인 | `POST /api/v1/auth/login` 신규 도입 |
| 응답 DTO | `AuthExchangeResponse` 재사용 (`userId`, `accessToken`, `refreshToken`, `expiresInSeconds`) |
| 실패 응답 | `LOGIN_INVALID` 로 계정 없음/비밀번호 불일치 통합 |
| 계정 linking | Non-Goal. OAuth email collision 자동 링크 금지 정책 유지 |

**구현 대상**: `AuthLoginController` + `AuthLoginService` + LOCAL 계정 조회 repository method + focused tests
