---
api_id: auth.exchange
http_method: POST
path: /api/v1/auth/exchange
auth: N
controller: AuthExchangeController.kt
handler: exchange
status: mined
---

# POST /api/v1/auth/exchange — 일회성 인증 코드(authCode)를 토큰으로 교환

## 1. 요청 (Request)
- Body: `AuthDto.ExchangeRequest` (`@Valid`) — 일회성 코드(OAuth callback에서 발급).

## 2. 응답 (Response)
- 성공: `200 OK` + `AuthDto.ExchangeResponse` (accessToken / refreshToken / 사용자 식별자 등)
- 실패: 400 (validation), 401 (코드 만료/무효)

## 3. 비즈니스 로직 (요약)
1. `AuthExchangeFacade.exchange(command)` 호출 → 일회성 코드 검증 후 액세스/리프레시 토큰 발급.

## 4. 데이터 의존
- DB read: 인증 코드 저장소(DB or Redis 추정)
- DB write: 리프레시 토큰 저장
- Redis: 코드 → 사용자 매핑 가능성
- Kafka: 없음
- 외부 API: 없음

## 5. 예외 케이스
- 만료/무효 코드 → 401
- 재사용된 코드 → 거부

## 6. 암묵적 로직 (Implicit)
- 일회성 코드(OAuth callback 결과)를 토큰으로 교환하는 BFF 엔드포인트로 추정.
- 토큰 응답 포맷은 `AuthDto.ExchangeResponse` 단일 (refresh도 같은 응답 구조 공유 — `AuthRefreshController`).

## 7. 호출자 (Clients)
- 프론트엔드 SPA (OAuth callback 직후)

## 8. TODO / Open Questions
- [ ] 코드 TTL 정책 확인
- [ ] 재사용 방지(one-time) 구현 위치 확인

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
