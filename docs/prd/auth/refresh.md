---
api_id: auth.refresh
http_method: POST
path: /api/v1/auth/refresh
auth: N (refreshToken 검증으로 대체)
controller: AuthRefreshController.kt
handler: refresh
status: mined
---

# POST /api/v1/auth/refresh — 리프레시 토큰으로 액세스 토큰 재발급

## 1. 요청 (Request)
- Body: `RefreshRequest { refreshToken: String (@NotBlank) }`

## 2. 응답 (Response)
- 성공: `200 OK` + `AuthDto.ExchangeResponse` (재발급된 access + refresh)
- 실패: 400 (validation), 401 (만료/무효)

## 3. 비즈니스 로직 (요약)
1. `AuthRefreshFacade.refresh(refreshToken)` 호출 → 토큰 검증 후 신규 토큰 페어 발급.

## 4. 데이터 의존
- DB read: 저장된 리프레시 토큰
- DB write: 토큰 회전(rotation) 시 신규 저장 + 구버전 폐기

## 5. 예외 케이스
- 만료/무효 → 401

## 6. 암묵적 로직 (Implicit)
- exchange와 동일한 응답 DTO(`ExchangeResponse`) 재사용 — refresh가 사실상 재발급.
- 토큰 회전(rotation) 적용 여부 미확인.

## 7. 호출자 (Clients)
- 프론트엔드 / 모바일 — 인터셉터에서 401 시 자동 호출

## 8. TODO / Open Questions
- [ ] refresh 토큰 회전(rotation) 정책 확인
- [ ] reuse detection (탈취 감지) 구현 여부

## 9. KEEP/DROP/FIX 분류

**판정: KEEP (with FIX)** — 2026-04-27 (구현은 PR4+ 예정)

PRD 의 핵심 흐름(refresh 토큰 → 새 access+refresh 페어 발급) KEEP. 다음 항목 FIX:

| 변경점 | 근거 |
|---|---|
| TODO §8 의 "rotation 정책" → **회전형 refresh 채택. 매 refresh 시 신규 sessionId·신규 refresh JWT 발급, 구 refresh 즉시 폐기** | [ADR-0001 세부 정책 표](../../adr/0001-authentication-architecture.md#세부-정책) |
| TODO §8 의 "reuse detection" → **이미 폐기된 refresh 가 재사용 시 → 해당 user 의 모든 sessionId 폐기 (의심 세션 일괄 무효화)** | PR4 구현 시점 명세 |
| AuthRefreshFacade 명칭 → **`AuthRefreshService`**. Facade 도입 안 함 | DDD 일관성 |
| Refresh JWT 가 만료된 경우 응답 — **401 + ErrorCode `REFRESH_EXPIRED`** (SPA 가 로그인 화면으로 redirect) | PR4 구현 시점 명세 |

**구현 PR**: PR4 (auth/refresh + auth/logout 함께)
