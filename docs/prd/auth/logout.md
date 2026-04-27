---
api_id: auth.logout
http_method: POST
path: /api/v1/auth/logout
auth: Y
controller: AuthLogoutController.kt
handler: logout
status: mined
---

# POST /api/v1/auth/logout — 로그아웃 (리프레시 토큰 폐기)

## 1. 요청 (Request)
- Header: `@LoginUser` → 인증 필수
- Body: 없음

## 2. 응답 (Response)
- 성공: `204 No Content`
- 실패: 401 (인증 실패)

## 3. 비즈니스 로직 (요약)
1. `AuthLogoutFacade.logout(userId)` 호출 → 사용자 리프레시 토큰 무효화.

## 4. 데이터 의존
- DB write: 리프레시 토큰 / 세션 폐기
- Redis: 토큰 블랙리스트 가능성

## 5. 예외 케이스
- 인증 실패 → 401

## 6. 암묵적 로직 (Implicit)
- 액세스 토큰 자체는 stateless라면 만료까지 유효 (블랙리스트 없으면). 확인 필요.

## 7. 호출자 (Clients)
- 프론트엔드 SPA / 모바일

## 8. TODO / Open Questions
- [ ] 액세스 토큰 즉시 무효화 정책(블랙리스트) 여부

## 9. KEEP/DROP/FIX 분류

**판정: KEEP (with FIX)** — 2026-04-27 (구현은 PR4+ 예정)

PRD 의 핵심 흐름(인증된 user 의 refresh 토큰/세션 폐기) KEEP. 다음 항목 FIX:

| 변경점 | 근거 |
|---|---|
| TODO §8 의 "액세스 토큰 즉시 무효화" → **현재 sessionId 만 blacklist 등록** (`bl:sid:{sid}` Redis SET) — access JWT 의 `sid` claim 으로 즉시 거부 | [ADR-0001 세부 정책 표](../../adr/0001-authentication-architecture.md#세부-정책) |
| **단일 디바이스 로그아웃**이 기본. PRD 본문의 "사용자 리프레시 토큰 무효화" 는 현재 sid 만 폐기로 명세 | PR4 구현 시점 |
| **전 디바이스 로그아웃**은 별도 endpoint (`POST /auth/logout/all`) 로 추후 분리. 본 PRD 는 단일 디바이스 한정 | PR4+ |
| AuthLogoutFacade 명칭 → **`AuthLogoutService`**. Facade 도입 안 함 | DDD 일관성 |
| `@LoginUser` 추출은 access JWT verifier (`common-auth-jwt`) 가 채운 SecurityContext 또는 ArgumentResolver 가 제공 | PR4 구현 시점 |

**구현 PR**: PR4 (auth/refresh + auth/logout 함께)
