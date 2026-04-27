# ADR-0006: OAuth Provider 통합 — PKCE / deviceId / 사용자 매칭 / Client 설계

- **상태**: Accepted
- **결정일**: 2026-04-27
- **결정자**: jonghun
- **관련 ADR**: [ADR-0001 인증 아키텍처](0001-authentication-architecture.md), [ADR-0002 서비스 분할](0002-service-decomposition.md)
- **관련 PRD**: [`auth/start.md`](../prd/auth/start.md), [`auth/callback.md`](../prd/auth/callback.md), [`auth/exchange.md`](../prd/auth/exchange.md)

## 컨텍스트

ADR-0001 은 OAuth2 + PKCE + RS256 JWT + Redis 세션 모델을 채택했지만, **provider 별 통합의 구체 정책**은 후속 결정으로 남겨졌다. PR3 (OAuth start/callback/exchange 구현) 착수 시점에 다음 5개 결정이 필요해 본 ADR 로 한 번에 확정한다.

대상 provider: **KAKAO / NAVER / GOOGLE** (3개). 클라이언트는 SPA + 모바일 (자체), 외부 OIDC 공개 없음.

---

## 결정 1 — PKCE 적용 정책 (per-provider flag)

### 검토한 옵션

| 옵션 | 장점 | 단점 |
|---|---|---|
| **A. 모든 provider 에 PKCE 강제** | RFC 7636 모범 사례. SPA 보안 강화 | Kakao/Naver 의 PKCE 지원이 표준화돼 있지 않음 (Naver 미지원, Kakao 부분 지원 가능성). 미지원 provider 와 호환성 깨짐 |
| **B. provider 별 `pkceEnabled` flag (선택)** | provider 호환성 보장. Google 은 즉시 활성화, Kakao/Naver 는 검증 후 활성화 가능 | 코드/설정 분기 추가. 일부 provider 에서 PKCE 보안 이점 미적용 |
| **C. PKCE 전체 미사용** | 가장 단순. 모든 provider 호환 | ADR-0001 의 "PKCE 필수" 결정과 충돌. SPA 가 발급된 인가코드를 가로챌 위험 (BFF 패턴이라 위험은 낮음) |

### 결정 — **B 채택**

`OAuthProviderProperties.ProviderConfig.pkceEnabled` 플래그로 provider 별 토글. 기본값:
- **Google**: `true` (표준 OAuth2 + PKCE 완전 지원)
- **Kakao**: `false` (공식 문서상 미지원, 추후 검증 후 활성화 가능)
- **Naver**: `false` (PKCE 미지원)

### 근거

- ADR-0001 의 "PKCE 필수" 는 **PKCE 를 지원하는 provider 에 한해서** 적용한다고 본 ADR 로 보강 해석. BFF 패턴이라 server↔provider 흐름이고 SPA 는 가로챌 인가코드를 직접 보지 않으므로(우리 자체 authCode 만 받음) 미지원 provider 의 위험은 ADR-0001 의 "Redis blacklist + state 쿠키 + 5분 TTL flowSession" 으로 mitigate.
- 옵션 A 를 강제하면 호환성 깨져 즉시 장애. 옵션 C 는 ADR-0001 의 의도와 명백히 충돌.

### 결과 및 영향

- 미적용 provider 의 보안은 (a) state 쿠키 SameSite=Lax + HttpOnly + Secure (b) flowSession Redis 5분 TTL (c) 자체 authCode 60s TTL one-time consume 로 보강.
- 후속: Kakao/Naver 가 PKCE 표준화 시 application.yaml 의 flag 만 변경하면 활성화.
- **보강 (2026-04-27, security review M1)**: `pkceEnabled=false` 인 provider 에 verifier 가 호출자로부터 전달돼도 silently 누락하지 않고 `log.warn` 으로 명시 — 운영자가 PKCE 정책 misconfig 를 인지하도록. silent no-op 안티패턴 회피.
- **보강 (2026-04-27, security review M2 / RFC 6749 §5.1)**: token 응답에 `token_type` 이 명시되면 `bearer` 만 허용 — `mac` 등 token-type confusion 방어.

---

## 결정 2 — deviceId 출처와 fallback

### 컨텍스트

ADR-0001 은 access JWT 의 `device(deviceId)` claim 을 정의했다. 단일 디바이스 로그아웃 / 활동 추적의 키. 그런데 OAuth start 시점에 deviceId 를 어디서 받을지가 미정.

### 검토한 옵션

| 옵션 | 장점 | 단점 |
|---|---|---|
| **A. SPA/모바일이 매 요청마다 `X-Device-Id` 헤더 전송** | 영속 deviceId 로 단일 디바이스 로그아웃이 정확. 네이티브 앱 친화적 | SPA 가 첫 방문 시 deviceId 발급/저장 로직 필요 |
| **B. 서버가 항상 새 UUID 생성** | 클라이언트 변경 없음 | 같은 디바이스가 매번 새 deviceId — "다른 기기에서 로그아웃" 의미 깨짐 |
| **C. 헤더 없으면 서버 fallback (web-{UUID})** | A + B 의 절충. SPA 미구현 단계에서도 기능 동작 | fallback deviceId 가 진짜 디바이스를 식별 못함 → 사용자 인지 필요 |

### 결정 — **C 채택 (A 우선 + fallback)**

- 우선순위: 요청 헤더 `X-Device-Id` 사용
- 헤더 부재 시: `web-{UUID}` fallback 생성
- fallback deviceId 는 **응답 쿠키 `jamo_device_id` (HttpOnly, 1년)** 로 SPA 에 전달해 다음 요청부터 헤더로 회귀하도록 유도

### 근거

- A 만 강제하면 PR3 단계에서 SPA 가 미구현이라 즉시 동작 불가.
- B 는 ADR-0001 의 "단일 디바이스 로그아웃" 요구를 못 만족.
- C 는 단계적 도입 가능 — 현재는 fallback 동작, SPA 가 헤더 보내기 시작하면 자동으로 정확해진다.

### 결과 및 영향

- 단일 디바이스 로그아웃의 정확도는 **클라이언트의 `X-Device-Id` 채택률에 비례**. 측정 metric 추가 권장 (PR4+ 로그아웃 구현 시 같이).
- 모바일 앱 구현 시 install ID (Android: AdvertisingIdClient / iOS: identifierForVendor) 를 deviceId 로 사용 권장.

---

## 결정 3 — Provider nickname / DisplayName 길이 처리

### 컨텍스트

`DisplayName` VO 는 32자 제한. Kakao/Naver/Google 의 nickname 은 더 길 수 있음. OAuth 가입 시 어떻게 처리?

### 검토한 옵션

| 옵션 | 장점 | 단점 |
|---|---|---|
| **A. 32자 초과 시 가입 실패** | 도메인 불변식 엄격 보존 | UX 파괴 — 사용자가 자기 닉네임이 왜 거부되는지 모름 |
| **B. 32자로 truncate** | 가입 항상 성공. 자동 처리 | 일부 닉네임 잘려 의미 손상 가능 |
| **C. DisplayName 제한을 64자 등으로 완화** | provider 와 호환 | 다른 도메인 화면/UI 의 폭 가정 깨짐 |

### 결정 — **B 채택**

OAuth 흐름의 application service 가 provider nickname 을 받아 `DisplayName.truncate(raw, 32)` 로 변환 후 User 등록. 잘림 발생 시 응답에 `displayNameTruncated: true` 플래그를 포함해 SPA 가 사용자에게 "수정하시겠어요?" 유도 가능.

### 근거

- A 는 첫인상부터 가입 실패 — 신규 가입 funnel 손실.
- C 는 이미 정해진 도메인 불변식을 흔드는 결정 → 전 모듈에 파급.
- B 는 도메인 불변식을 보존하면서 UX 도 보장. 잘림 신호는 PR3-b 의 `OAuthCallbackResult`/`AuthExchangeResponse` 에서 노출.

### 결과 및 영향

- `DisplayName` 에 `static DisplayName truncated(String raw)` 추가. 32자 이하면 그대로, 초과면 32자로 자른 새 인스턴스 + `wasTruncated()` 플래그 보유.

---

## 결정 4 — 이메일 중복 시 자동 링크 여부 (MSA 정합성)

### 컨텍스트

Provider 가 반환한 email 이 이미 우리 DB 의 다른 User 에 존재하는 경우 (예: 같은 사람이 LOCAL 가입 후 KAKAO 로 다시 로그인, 또는 GOOGLE 로 가입 후 KAKAO 로 로그인). 자동으로 같은 User 로 링크할지 판단 필요.

### 검토한 옵션

| 옵션 | 장점 | 단점 |
|---|---|---|
| **A. 같은 email → 같은 User 자동 링크** | 사용자 입장에서 "내 계정은 하나" 느낌. 중복 계정 방지 | **계정 탈취 위험** (악성 사용자가 피해자 email 로 다른 provider 가입 시도). MSA 환경에서 email 의 "고유성" 은 provider 가 보증하는데 provider 마다 확인 절차 차이가 큼 (Kakao 는 사용자가 거부 가능). 나중에 분리 요구 시 아주 복잡 |
| **B. 자동 링크 X — 새 User 로 등록** | 보안 안전. provider 별 user 격리 명확. MSA 분산 환경에서 user 식별 책임이 명확해짐 (provider+providerUserId 가 SoT) | 사용자가 "내 계정이 두 개" 라고 느낄 수 있음. 명시적 link/merge 기능을 별도로 만들어야 함 |
| **C. 같은 email 차단 (가입 거부)** | 중복 0 보장 | 같은 email 을 다른 사람이 쓰는 정당 케이스(가족 공유) 차단. UX 파괴 |

### 결정 — **B 채택 (자동 링크 안 함, 새 User 로 등록)**

Provider 와 providerUserId 의 조합으로만 User 를 매칭. email 은 보조 정보로만 저장하며 unique constraint 두지 않음. 기존 같은 email User 가 있어도 무시하고 새 User + 새 OAuthIdentity 생성.

### 근거 (MSA 도메인 관점에서 깊은 고민)

1. **Identity의 SoT 는 OAuth provider** — 우리 DB 의 email 은 provider 가 시점 t 에 알려준 스냅샷일 뿐. 같은 email 이 같은 사람을 의미한다는 보장은 어디에도 없음 (provider 가 email 검증을 안 한 케이스, 사용자가 email 을 변경했다가 다른 사람이 reuse 한 케이스 등).
2. **계정 탈취 (account takeover) 회피** — 자동 링크는 가장 흔한 OAuth 취약점 패턴 중 하나 (OAuth account hijacking via email collision). 피해자가 KAKAO 로 가입 후, 공격자가 "verified email 안 거치는 provider" 로 같은 email 가입 → 자동 링크 → 피해자 계정 탈취. provider 의 email verified 정책이 일관적이지 않으므로 신뢰 불가.
3. **MSA 분산 환경의 일관성** — auth 가 다른 서비스(diary, chat, profile)와 user 식별을 공유할 때, "provider+providerUserId" 가 단일 SoT 면 식별 갱신/충돌 처리가 한 곳(identity-service)으로 격리. email 자동 링크는 여러 도메인에서 user 매칭 로직이 흩어지게 하는 시작점이 됨.
4. **사후 link 가 사후 unlink 보다 훨씬 쉽다** — 명시적 "계정 연결" 화면을 만드는 비용 < 잘못 링크된 사용자를 안전하게 분리하는 비용. 최초 결정의 보수성을 우선.

### 결과 및 영향

- 같은 email 로 여러 User 가 존재할 수 있음 — 프로필 조회/검색에서 중복 닉네임/email 가능성을 UI 가 인지해야 함.
- `users.email` 컬럼에 `UNIQUE` 제약 두지 않음 (이미 nullable, no-unique 로 되어 있음 — 본 결정과 정합).
- 명시적 계정 연결 (`POST /auth/link/{provider}`) 은 **별도 PR(향후)** 로 분리. 본 ADR 범위 외.
- 사용자 안내: 회원가입 후 "다른 소셜로 같은 이메일이 있어요. 연결하시겠어요?" 화면을 SPA 가 띄우는 흐름은 PRD 추가 시 재검토.

---

## 결정 5 — OAuth Client 설계 (단일 RestClient + per-provider Extractor)

### 컨텍스트

3개 provider 모두 OAuth2 Authorization Code 흐름이지만 token / userinfo 응답 JSON 구조가 모두 다름:
- Kakao userinfo: `{ id, properties:{nickname}, kakao_account:{email, ...} }`
- Naver userinfo: `{ resultcode, message, response:{ id, nickname, email } }`
- Google userinfo: `{ sub, name, email, email_verified }`

Token endpoint 는 형태가 거의 같음 (`access_token`, `refresh_token`, `expires_in`).

### 검토한 옵션

| 옵션 | 장점 | 단점 |
|---|---|---|
| **A. provider 별 클라이언트 클래스 3개** (KakaoOAuthClient / NaverOAuthClient / GoogleOAuthClient) | 각 provider 특수성 격리 | HTTP 호출 보일러플레이트 3중 중복. 신규 provider 추가 시 클래스 통째로 추가 |
| **B. 단일 HttpOAuthProviderClient + provider 별 UserInfoExtractor 전략** | HTTP 골격 1곳. 신규 provider = extractor 1개만 추가. 테스트 격리 쉬움 | extractor map 의존성 주입 정확해야 함 (provider enum → extractor 매핑) |
| **C. Spring Security OAuth2 client 사용** | 라이브러리 검증, RFC 준수 | BFF + 자체 authCode 발급 흐름과 자연스럽게 안 맞음. ADR-0001 의 stateless 자체 JWT 모델과 통합 비용 큼. Spring Security 학습 곡선 + 우리 흐름 커스터마이징이 오히려 복잡 |

### 결정 — **B 채택**

```
domain/service/OAuthProviderClient (port)
    ↑ implements
infrastructure/external/HttpOAuthProviderClient
    ├─ RestClient (Spring 6 표준 HTTP client)
    ├─ Map<OAuthProvider, OAuthUserInfoExtractor> (전략 패턴)
    │   ├─ KakaoUserInfoExtractor
    │   ├─ NaverUserInfoExtractor
    │   └─ GoogleUserInfoExtractor
    └─ 흐름: token POST → extract access_token → userinfo GET → extractor.extract(JsonNode) → OAuthUserInfo
```

### 근거

- A 의 코드 중복은 PR4+ 에서 retry/circuit breaker 적용 시 3곳 모두 수정 부담 → 유지보수성 나쁨.
- C 는 우리가 BFF 패턴 + 자체 authCode 발급이라 Spring Security 의 표준 흐름이 오히려 우회 비용 큼. ADR-0001 결정의 "운영 단순함" 정신과도 정합.
- B 는 추가 provider (예: APPLE) 시 extractor 1개만 작성하면 됨 → 변경 영향 최소.

### 결과 및 영향

- WireMock 단위 테스트는 **extractor 별 1개 + HttpOAuthProviderClient 1개로 token+userinfo 통합 흐름 검증** 으로 분리.
- **에러 매핑 정책 (2026-04-27, security review H1)**:
  - `RestClientResponseException` 의 cause 는 끊고 status code 만 메시지에 보존 — provider 응답 본문이 cause chain 으로 누출되는 것을 차단.
  - `RestClientException` 까지 catch 범위 확대 — connection reset / read timeout / 응답 파싱 실패 등 모든 client-side 예외를 `OAuthProviderCallFailedException` 으로 sanitize 후 래핑.
  - 호출자(`@RestControllerAdvice`) 는 `OAuthAuthenticationException` 계열을 catch 해 **고정 ErrorCode 만 응답** — `OAuthProviderCallFailedException` javadoc 에 의무 명시.
- **TLS 강제 (2026-04-27, security review H3)**:
  - `ProviderConfig` compact constructor 가 `tokenUrl` / `userinfoUrl` 의 `https://` 강제. testing 한정으로 `localhost` / `127.0.0.1` 예외.
  - 운영자 misconfig 시 application 시작 자체가 fail-fast.
- **provider key 정규화 (2026-04-27, security review M4)**:
  - `OAuthProviderProperties` compact constructor 가 yaml key 를 lowercase 로 자동 정규화. `KAKAO` / `Kakao` / `kakao` 모두 동일 lookup. case 차이로 인한 운영 incident 방지.
- 신규 provider 추가 시 체크리스트:
  1. `OAuthProvider` enum 에 추가
  2. `XxxUserInfoExtractor` 추가 + 단위 테스트 (invalid email silent drop 케이스 포함)
  3. `application.yaml jamo.oauth.providers.xxx` 설정 (https 강제)
  4. WireMock 통합 테스트 1건 추가 (happy + 4xx + IO error + PKCE on/off)

---

## 후속 결정이 필요한 항목

- [x] **state 쿠키 SameSite 속성** → `Lax` 채택. cookie 속성 전체는 [decisions/auth/cookie-policy.md](../decisions/auth/cookie-policy.md) (PR3-c, 2026-04-27)
- [x] **`@RestControllerAdvice` 에서 ErrorCode 매핑** → `AuthExceptionHandler` 가 `AuthCodeNotFound/Expired` → `AUTH_CODE_INVALID` (401). Callback 의 OAuth 예외는 controller try-catch → `frontendBaseUrl/auth/error?code=<ErrorCode>` 302 redirect (PR3-c, 2026-04-27)
- [x] **`frontend.base-url` fail-fast 검증** → `FrontendProperties` compact constructor 가 http/https scheme + URI 형식 강제. 화이트리스트 host 검증 (Open Redirect 추가 방어) 은 별도 운영 PR (PR3-c, 2026-04-27, code review M4)
- [x] **`/api/v1/auth/oauth/{provider}/start` log injection 방어** → controller 가 OAuth 2.0 RFC 6749 §4.1.2.1 error code 화이트리스트 (`^[a-z_]{1,40}$`) 만 그대로 로깅, 그 외는 `<invalid>` (PR3-c, 2026-04-27, security review M1)
- [ ] **PR4+**: 명시적 계정 연결 (`POST /auth/link/{provider}`) API 도입 시점 — UX 요구 수렴 후
- [ ] **PR4+**: deviceId 채택률 metric 수집 + fallback 비율 alarming
- [ ] **운영**: provider 별 Circuit Breaker / Retry / Fallback 정책 (Resilience4j) — gRPC 와 동일하게 외부 호출 SLO 정의 필요
- [ ] **운영**: response body size limit (현재는 무제한) — DNS rebind / 침해된 provider 의 메모리 압박 회피
- [ ] **보안**: provider 응답 email 의 `email_verified=true` 만 신뢰할지 결정 (Google 은 명시적, Kakao/Naver 는 모호) — 현재는 모두 raw email 저장. ADR-0006 결정 4 의 "email 자동 링크 X" 가 mitigate 함

## 참고

- 관련 ADR: [ADR-0001 인증 아키텍처](0001-authentication-architecture.md), [ADR-0002 서비스 분할](0002-service-decomposition.md)
- PRD: [`auth/start.md`](../prd/auth/start.md), [`auth/callback.md`](../prd/auth/callback.md), [`auth/exchange.md`](../prd/auth/exchange.md)
- 외부 표준: RFC 6749 (OAuth 2.0), RFC 7636 (PKCE)
- 보안 참고: OWASP — OAuth Account Hijacking via Email Collision
