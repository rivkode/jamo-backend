---
api_id: auth.oauthCallback
http_method: GET
path: /api/v1/auth/oauth/{provider}/callback
auth: N
controller: OAuthBrowserController.kt
handler: callback
status: mined
---

# GET /api/v1/auth/oauth/{provider}/callback — OAuth 콜백 처리

## 1. 요청 (Request)
- Path: `provider`
- Query: `code?`, `state?`, `error?`
- Cookie: state 쿠키 (start에서 설정)

## 2. 응답 (Response)
- 성공: `302 Found` → `${frontendBaseUrl}/auth/callback?code=<authCode>` (서비스 자체 일회성 코드)
- 실패: `302 Found` → `${frontendBaseUrl}/auth/error?code=<ErrorCode>` (OAUTH_AUTHORIZATION_FAILED / OAUTH_STATE_INVALID)

## 3. 비즈니스 로직 (요약)
1. `parseProvider(name)` (LOCAL 거부)
2. 쿠키에서 storedState 조회
3. `error` 있거나 `code` 비면 → errorRedirect(OAUTH_AUTHORIZATION_FAILED)
4. state mismatch (storedState != state, 둘 다 비면도 실패) → errorRedirect(OAUTH_STATE_INVALID)
5. `oauthFacade.login(OAuthLoginCommand(provider, code))` → 자체 authCode 발급
6. 프론트 callback URL로 302
7. **finally**: state 쿠키 clear

## 4. 데이터 의존
- DB write: 신규 사용자 가입 또는 기존 매핑 (OAuthFacade 내부)
- 외부 API: 카카오/네이버/구글 토큰 교환 (Facade 내부)
- 쿠키: state 쿠키 read/clear

## 5. 예외 케이스
- `error` 파라미터 존재 또는 `code` 없음 → OAUTH_AUTHORIZATION_FAILED
- state mismatch → OAUTH_STATE_INVALID
- `OAuthAuthenticationException` 잡아서 errorRedirect

## 6. 암묵적 로직 (Implicit)
- **state 쿠키는 성공/실패 모두 finally에서 clear** — 재사용 차단
- `URLEncoder.encode(..., UTF_8)`로 쿼리 인코딩
- error 응답도 200이 아니라 302 redirect

## 7. 호출자 (Clients)
- OAuth provider (브라우저 redirect)

## 8. TODO / Open Questions
- [ ] OAuthFacade.login 내부 신규 가입 정책
- [ ] state 쿠키의 보안 속성 (HttpOnly/Secure/SameSite)

## 9. KEEP/DROP/FIX 분류

**판정: KEEP (with FIX)** — 2026-04-27, ADR-0006 와 함께 결정

PRD 의 핵심 흐름(state 검증 → token+userinfo 호출 → 자체 authCode 발급 → 프론트 redirect → finally 쿠키 clear)은 그대로 유지. 다음 항목 FIX:

| 변경점 | 근거 |
|---|---|
| TODO §8 의 "OAuthFacade.login 내부 신규 가입 정책" → **명시화: provider+providerUserId 로 매칭, 없으면 새 User 등록. 같은 email 이 다른 User 에 있어도 자동 링크 안 함** | [ADR-0006 결정 4](../../adr/0006-oauth-provider-integration.md#결정-4--이메일-중복-시-자동-링크-여부-msa-정합성) |
| TODO §8 의 "state 쿠키 보안 속성" → start.md §9 와 동일하게 ADR-0001 + ADR-0006 으로 위임 | [ADR-0001](../../adr/0001-authentication-architecture.md) |
| state 검증을 단순 cookie==query 비교에서 **flowSession Redis 조회 + state match + PKCE verifier 회수** 로 확장 | [ADR-0006 결정 1](../../adr/0006-oauth-provider-integration.md) |
| Provider nickname 길이 처리 → **`DisplayName.truncated()` 32자 자동 잘림 + `displayNameTruncated` 플래그** | [ADR-0006 결정 3](../../adr/0006-oauth-provider-integration.md#결정-3--provider-nickname--displayname-길이-처리) |
| OAuthFacade 명칭 → **`OAuthCallbackService` (application service)**. Facade 패턴 도입 안 함 (단일 진입점이라 불필요한 간접 계층) | DDD 구현 PR3-b |

**구현 PR**: PR3-a (OAuthProviderClient port + HTTP adapter), PR3-b (OAuthCallbackService + Controller + UserRegistration)
