---
api_id: profile.retrieveMyProfile
http_method: GET
path: /api/v1/profiles/me
auth: Y
controller: ProfileApiController.kt
handler: retrieveMyProfile
status: mined
---

# GET /api/v1/profiles/me — 내 프로필 조회

## 1. 요청 (Request)
- Header: `@LoginUser`

## 2. 응답 (Response)
- 성공: `200 OK` + `ProfileDto.ProfileResponse`

## 3. 비즈니스 로직 (요약)
1. `profileFacade.retrieveMyProfile(userId)` → 본인 프로필 반환 (private 정보 포함 가능).

## 4. 데이터 의존
- DB read: users / profiles

## 5. 예외 케이스
- 인증 실패 → 401

## 6. 암묵적 로직 (Implicit)
- public profile과 응답 DTO가 동일(`ProfileResponse`) — private 필드 포함 정책 확인 필요.

## 7. 호출자 (Clients)
- 모바일/웹 (마이페이지)

## 8. TODO / Open Questions
- [ ] private vs public 필드 분리

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
