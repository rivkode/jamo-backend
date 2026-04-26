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

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
