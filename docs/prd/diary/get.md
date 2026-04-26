---
api_id: diary.get
http_method: GET
path: /api/v1/diaries/{diaryId}
auth: Y
controller: DiaryController.kt
handler: get
status: mined
---

# GET /api/v1/diaries/{diaryId} — 일기 상세

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `diaryId: Long`

## 2. 응답 (Response)
- 성공: `200 OK` + `DiaryDto.DiaryResponse`
- 실패: 404 / 403 (비공개 + 작성자 아님)

## 3. 비즈니스 로직 (요약)
1. `diaryFacade.getDetail(diaryId, userId)` → 일기 단건 + 사용자 컨텍스트(좋아요/저장 상태 등) 결합.

## 4. 데이터 의존
- DB read: diaries, diary_likes(여부), comments(count) 가능성

## 5. 예외 케이스
- 없음 → 404
- 비공개 + 비작성자 → 403/404 (Facade 책임)

## 6. 암묵적 로직 (Implicit)
- `userId`가 응답의 viewer-context에 사용되는지 확인 (좋아요 여부 포함 등).

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 조회수 증가 처리 위치(Facade vs 별도)
- [ ] 비공개 일기 접근 정책

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
