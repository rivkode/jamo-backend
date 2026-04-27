---
api_id: auth.oauthStart
http_method: GET
path: /api/v1/auth/oauth/{provider}/start
auth: N
controller: OAuthBrowserController.kt
handler: start
status: mined
---

# GET /api/v1/auth/oauth/{provider}/start — OAuth 인증 시작 (제공자 redirect)

## 1. 요청 (Request)
- Path: `provider` ∈ {KAKAO, NAVER, GOOGLE} (대소문자 무관, 내부적으로 `valueOf(uppercase)`)
- 인증 미요구

## 2. 응답 (Response)
- 성공: `302 Found` + `Location: <provider authorize URL>` + state 쿠키 설정
- 실패: state 생성 실패 외 — `LOCAL` 등 unsupported provider면 OAuthAuthenticationException

## 3. 비즈니스 로직 (요약)
1. `parseProvider(name)` → AuthProvider enum (LOCAL은 거부)
2. `providerConfig` (kakao/naver/google) 선택
3. `state = UUID.randomUUID()` → `OAuthStateCookieManager.set(response, provider, state)`
4. `UriComponentsBuilder`로 `response_type=code`, `client_id`, `redirect_uri`, `scope`, `state` 쿼리 부착
5. 302 redirect

## 4. 데이터 의존
- 외부 API: 없음 (URL 빌드만)
- 쿠키: state 쿠키 설정 (provider별 분리 추정)

## 5. 예외 케이스
- 지원하지 않는 provider → `OAuthAuthenticationException(OAUTH_AUTHORIZATION_FAILED)`
- LOCAL provider → 위와 동일 사유로 거부

## 6. 암묵적 로직 (Implicit)
- provider 이름은 case-insensitive (`uppercase()` 후 enum)
- 클라이언트는 이 endpoint를 직접 호출하지 않고 브라우저가 hit
- state는 매 요청 새 UUID, callback에서 쿠키와 비교

## 7. 호출자 (Clients)
- 사용자 브라우저 (직접 navigation)

## 8. TODO / Open Questions
- [ ] state 쿠키 SameSite/Secure 속성
- [ ] PKCE 적용 여부

## 9. KEEP/DROP/FIX 분류

**판정: KEEP (with FIX)** — 2026-04-27, ADR-0006 와 함께 결정

PRD 의 핵심 흐름(state 쿠키 + 302 redirect + provider별 authorize URL 빌드)은 그대로 유지. 다음 항목 FIX:

| 변경점 | 근거 |
|---|---|
| TODO §8 의 "PKCE 적용 여부" → **provider별 `pkceEnabled` flag 로 결정** (Google: true, Kakao/Naver: false) | [ADR-0006 결정 1](../../adr/0006-oauth-provider-integration.md#결정-1--pkce-적용-정책-per-provider-flag) |
| TODO §8 의 "state 쿠키 SameSite/Secure" → **`Lax` + HttpOnly + 운영 환경에서 Secure=true** | [ADR-0001](../../adr/0001-authentication-architecture.md), application.yaml `jamo.oauth.state-cookie` |
| `X-Device-Id` 헤더 신규 입력 (없으면 fallback `web-{UUID}`, 응답 쿠키 `jamo_device_id` 로 SPA 에 전달) | [ADR-0006 결정 2](../../adr/0006-oauth-provider-integration.md#결정-2--deviceid-출처와-fallback) |
| flowSession Redis 추가 — `state → {provider, pkceVerifier?, deviceId, redirectUri, expiresAt}` (TTL 5분) — state 쿠키만으로는 PKCE verifier 보관 불가 | [ADR-0006 결정 1·2](../../adr/0006-oauth-provider-integration.md) |

**구현 PR**: PR3-a (OAuth client port + adapter), PR3-b (Application + Controller + flowSession + state cookie)
