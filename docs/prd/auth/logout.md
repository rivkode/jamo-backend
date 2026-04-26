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

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
