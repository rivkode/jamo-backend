---
api_id: profile.updateMyProfile
http_method: PATCH
path: /api/v1/profiles/me
auth: Y
controller: ProfileApiController.kt
handler: updateMyProfile
status: mined
---

# PATCH /api/v1/profiles/me — 내 프로필 부분 수정

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: `ProfileDto.UpdateMyProfileRequest` (`@Valid`)

## 2. 응답 (Response)
- 성공: `200 OK` + `ProfileDto.ProfileResponse`

## 3. 비즈니스 로직 (요약)
1. `profileFacade.updateMyProfile(userId, request.toCommand())` → 부분 업데이트 후 반환.

## 4. 데이터 의존
- DB write: users / profiles

## 5. 예외 케이스
- validation 실패 → 400
- 닉네임 중복 등 도메인 규칙 → 409

## 6. 암묵적 로직 (Implicit)
- PATCH 의미 — null 필드는 변경 안 함이 일반적이지만 DTO 정의 확인 필요.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 변경 가능 필드 화이트리스트
- [ ] 닉네임 변경 빈도 제한

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
