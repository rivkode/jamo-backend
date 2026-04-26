---
api_id: user.getUserProfile
http_method: GET
path: /api/v1/users/{userId}
auth: Y
controller: UserApiController.kt
handler: getUserProfile
status: mined
---

# GET /api/v1/users/{userId} — 타 사용자 공개 프로필

## 1. 요청 (Request)
- Header: `@LoginUser` (viewerId)
- Path: `userId: Long`

## 2. 응답 (Response)
- 성공: `200 OK` + `UserDto.PublicProfileResponse`

## 3. 비즈니스 로직 (요약)
1. `userFacade.retrievePublicProfile(viewerId, userId)` → 뷰어 컨텍스트 포함 공개 프로필.

## 4. 데이터 의존
- DB read: users / profiles

## 5. 예외 케이스
- 사용자 없음 → 404

## 6. 암묵적 로직 (Implicit)
- **`/api/v1/profiles/{userId}` (ProfileApiController)와 기능 중복** — 두 endpoint가 공존하는 이유 확인 필요(`@FIX` 후보 — 통합).

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] users vs profiles 통합 결정

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: 중복 endpoint 통합 → `@FIX`
