---
api_id: profile.retrieveSavedClips
http_method: GET
path: /api/v1/profiles/{userId}/saved-clips
auth: N (⚠️ `@LoginUser` 없음)
controller: ProfileApiController.kt
handler: retrieveSavedClips
status: mined
---

# GET /api/v1/profiles/{userId}/saved-clips — 사용자가 저장한 클립 목록

## 1. 요청 (Request)
- Path: `userId: Long` (`@Positive`)
- Query: `ProfileSavedClipDto.FeedRequest { cursor, size, category }` (`@ModelAttribute @Valid`)
- 인증 미요구 (컨트롤러에 `@LoginUser` 없음)

## 2. 응답 (Response)
- 성공: `200 OK` + `ProfileSavedClipDto.FeedResponse`

## 3. 비즈니스 로직 (요약)
1. `profileFacade.retrieveSavedClips(profileUserId=userId, cursor, size, category)` → 페이지 응답.

## 4. 데이터 의존
- DB read: saved_clips × clips (조인)

## 5. 예외 케이스
- userId ≤ 0 → 400
- 없는 사용자 → 빈 목록 또는 404

## 6. 암묵적 로직 (Implicit)
- **인증 미요구** — 다른 사용자의 저장 클립이 공개 의도인지 확인 필요(`@FIX`/`@KEEP` 결정).
- 다른 profile endpoint는 인증 요구 — 이 하나만 다름 → 의도 vs 실수.

## 7. 호출자 (Clients)
- 모바일/웹 (타 사용자 프로필 화면)

## 8. TODO / Open Questions
- [ ] 비공개 저장 클립 노출 위험
- [ ] 인증 정책 일관성

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: 인증 정책 명확화 → `@FIX`
