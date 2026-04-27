# Decision: OAuth State Cookie + Device Cookie 정책

- **상태**: Accepted
- **결정일**: 2026-04-27
- **결정자**: jonghun
- **PR**: PR3-c (`feature/identity-auth-oauth-presentation`)
- **관련 ADR**: [ADR-0001 인증 아키텍처](../../adr/0001-authentication-architecture.md), [ADR-0006 OAuth Provider 통합](../../adr/0006-oauth-provider-integration.md)

## 컨텍스트

PR3-c 가 Presentation 계층을 도입하면서 두 종류의 cookie 를 발급한다:

1. **State cookie** — OAuth start↔callback 간 state 보관. ADR-0006 결정 1 / PRD `auth/start.md` 가 cookie 사용 명시.
2. **Device cookie** — `X-Device-Id` 헤더 부재 시 server fallback 을 클라이언트에 영속화 (ADR-0006 결정 2).

각 cookie 의 정확한 속성(`HttpOnly` / `Secure` / `SameSite` / `Path` / `Domain` / `Max-Age`)을 한 곳에 결정해 박는다.

## 검토한 옵션 (SameSite)

OAuth callback 의 핵심 제약: provider 가 **Top-level navigation (302)** 으로 우리 callback URL 로 redirect 한다. 우리 cookie 가 그 시점에 동봉되어야 state 검증이 가능.

| 옵션 | 호환성 | 보안 |
|---|---|---|
| **A. SameSite=Strict** | ❌ provider→우리 callback 의 cross-site 요청에 cookie 미동봉 → state 검증 불가 | 최강 |
| **B. SameSite=Lax** | ✅ Top-level GET navigation 에는 cookie 동봉 (callback 은 GET). POST 형식 callback 은 미동봉 | 강함 (CSRF 거의 차단) |
| **C. SameSite=None + Secure** | ✅ 모든 cross-site 요청 동봉 | 약함 — CSRF 토큰 별도 필요 |

### 결정 — SameSite=**Lax**

3개 provider 모두 callback 이 GET (RFC 6749 §4.1.2) 이므로 Lax 로 충분. CSRF 위험은 state cookie 자체 + flowSession 의 atomic GETDEL 로 mitigate.

## 검토한 옵션 (Path)

| 옵션 | 영향 |
|---|---|
| **A. Path=/** | 모든 API 에 cookie 동봉 — 서버 부하 + 잠재적 leak 표면 |
| **B. Path=/api/v1/auth/oauth (state)** | callback URL 만 매칭 — 다른 API 에 안 보냄. 의도 명확 |
| **C. Path=/ (device)** | 모든 API 에 동봉. deviceId 가 모든 인증 흐름에서 사용되므로 필요 |

**Decision**:
- **State cookie**: `Path=/api/v1/auth/oauth` — callback 경로 한정.
- **Device cookie**: `Path=/` — 모든 API (X-Device-Id 헤더 fallback 으로 사용).

## 결정된 속성 (전체)

### State Cookie

| 속성 | 값 | 근거 |
|---|---|---|
| Name | `oauth_state_<provider>` (소문자 provider) | provider 별 분리 — 동시 다중 provider 시도 방지 + PRD 와 정합 |
| HttpOnly | **true** | XSS 시 JS 접근 차단 |
| Secure | yaml flag (운영=true, 로컬=false) | 운영 HTTPS 강제 |
| SameSite | **Lax** | callback (GET) 호환성 + CSRF 방어 |
| Path | `/api/v1/auth/oauth` | callback 경로 한정 |
| Domain | yaml flag (운영=`jamoai.app`, 로컬=null) | 서브도메인 공유 |
| Max-Age | **300s (5분)** | flowSession Redis TTL 과 동기화 |

### Device Cookie

| 속성 | 값 | 근거 |
|---|---|---|
| Name | `jamo_device_id` | 단일 cookie (provider 무관) |
| HttpOnly | **true** | XSS 시 JS 접근 차단 |
| Secure | yaml flag | 운영 HTTPS 강제 |
| SameSite | **Lax** | 모든 인증 흐름 호환 |
| Path | `/` | 모든 API (X-Device-Id fallback) |
| Domain | yaml flag | 서브도메인 공유 |
| Max-Age | **31536000s (1년)** | 디바이스 영속 식별 — 로그인 세션보다 길다 |

## 운영 envvar 매핑

```yaml
jamo:
  oauth:
    state-cookie:
      domain: ${STATE_COOKIE_DOMAIN:}      # 운영: jamoai.app
      secure: ${STATE_COOKIE_SECURE:false} # 운영: true
      same-site: Lax
      max-age: PT5M
    device-cookie:
      domain: ${DEVICE_COOKIE_DOMAIN:}     # 운영: jamoai.app
      secure: ${DEVICE_COOKIE_SECURE:false} # 운영: true
      same-site: Lax
      max-age: P365D
```

`Secure=false` 의 default 는 로컬 개발 편의 — **운영 deploy 시 envvar override 필수**.
운영 prod profile 분리는 별도 PR (운영 ops 정리 PR) 로 격상.

## 결과 및 영향

### 보안
- **State cookie**: HttpOnly + SameSite=Lax + Path 한정 + 5분 TTL + atomic GETDEL → state replay / XSS / CSRF 모두 mitigate.
- **Device cookie**: HttpOnly + 1년 TTL → device 추적은 server-side 만 (XSS 노출 없음). server-issued fallback 임을 사용자가 인지 못하게 자연스러움.

### UX
- 사용자가 brower cookie 차단 시 OAuth 흐름 자체가 실패 (state 검증 불가). 별도 안내 필요 — PR3-c 의 callback error redirect 에 `OAUTH_STATE_INVALID` 로 노출.
- Device cookie 차단 시 매 OAuth 흐름마다 새 deviceId 발급 — 단일 디바이스 로그아웃 효과 약화 (사용자 인지 가능).

### 후속 결정 항목

- [ ] **운영 prod profile 분리**: `application-prod.yaml` 에서 `STATE_COOKIE_SECURE=true`, `DEVICE_COOKIE_SECURE=true` default 강제. 별도 운영 ops PR.
- [ ] **모바일 앱**: WebView 가 아닌 native 앱이면 cookie 미사용 — `X-Device-Id` 헤더만 사용. 본 결정과 충돌 없음.
- [ ] **Device cookie rotation**: 1년 후 자동 갱신 또는 수동 갱신 정책 (PR4+ 의 device 관리 시).
- [ ] **CSRF 토큰**: SameSite=Lax 가 미흡한 경우 (POST callback 같은 비표준 provider 도입 시) 별도 CSRF 토큰 도입.

## 참고

- RFC 6265bis (Cookies: HTTP State Management Mechanism)
- OWASP Session Management Cheat Sheet — Cookie Attributes
- [PR3-a security review](https://github.com/rivkode/jamo-backend/pull/11) L3 — STATE_COOKIE_SECURE prod default
