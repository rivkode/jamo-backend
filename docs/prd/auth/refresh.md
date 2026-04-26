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

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
