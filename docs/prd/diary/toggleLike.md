---
api_id: diary.toggleLike
http_method: POST
path: /api/v1/diaries/{diaryId}/like
auth: Y
controller: DiaryLikeController.kt
handler: toggle
status: mined
---

# POST /api/v1/diaries/{diaryId}/like — 일기 좋아요 토글

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `diaryId: Long`
- Body: `DiaryLikeDto.ToggleRequest { liked: Boolean }` (`@Valid`)

## 2. 응답 (Response)
- 성공: `200 OK` + `DiaryLikeDto.ToggleResponse`

## 3. 비즈니스 로직 (요약)
1. `diaryLikeFacade.setLiked(diaryId, userId, request.liked)` → set/unset.

## 4. 데이터 의존
- DB write: diary_likes (upsert/delete)

## 5. 예외 케이스
- 일기 없음 → 404

## 6. 암묵적 로직 (Implicit)
- 명시적 `liked` 플래그로 멱등성 확보 (재시도 안전).

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 좋아요 수 응답 포함 여부

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
