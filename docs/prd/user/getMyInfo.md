---
api_id: user.getMyInfo
http_method: GET
path: /api/v1/users/me
auth: Y
controller: UserApiController.kt
handler: getMyInfo
status: mined
---

# GET /api/v1/users/me — 내 사용자 정보

## 1. 요청 (Request)
- Header: `@LoginUser`

## 2. 응답 (Response)
- 성공: `200 OK` + `UserDto.InfoResponse`

## 3. 비즈니스 로직 (요약)
1. `userFacade.retrieveUserInfo(userId)` → 사용자 정보(이메일, provider, 가입일 등 추정).

## 4. 데이터 의존
- DB read: users

## 5. 예외 케이스
- 인증 실패 → 401

## 6. 암묵적 로직 (Implicit)
- profile.retrieveMyProfile과 응답 분리 — UserInfo와 ProfileResponse의 책임 구분 확인 필요(중복 정보).

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] User vs Profile DTO/도메인 경계

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
