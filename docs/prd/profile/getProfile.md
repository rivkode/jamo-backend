---
api_id: profile.getProfile
http_method: GET
path: /api/v1/profiles/{userId}
auth: Y
controller: ProfileApiController.kt
handler: getProfile
status: mined
---

# GET /api/v1/profiles/{userId} — 타 사용자 프로필 조회

## 1. 요청 (Request)
- Header: `@LoginUser` (loginUserId)
- Path: `userId: Long` (`@Positive`)

## 2. 응답 (Response)
- 성공: `200 OK` + `ProfileDto.ProfileResponse`

## 3. 비즈니스 로직 (요약)
1. `profileFacade.retrieveProfile(loginUserId, userId)` → 뷰어 컨텍스트 포함 프로필.

## 4. 데이터 의존
- DB read: users / profiles, follow 관계 가능성

## 5. 예외 케이스
- 사용자 없음 → 404
- 0 이하 userId → 400

## 6. 암묵적 로직 (Implicit)
- loginUserId가 응답에 viewer-context(예: 팔로우 여부) 포함시키는지 확인.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 비공개 프로필 차단 정책

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
