---
api_id: diary.getFeed
http_method: GET
path: /api/v1/diaries/feed
auth: Y
controller: DiaryController.kt
handler: getFeed
status: mined
---

# GET /api/v1/diaries/feed — 공개 일기 피드

## 1. 요청 (Request)
- Header: `@LoginUser`
- Query: `cursor?: String`, `size?: Int (default 10)`, `sort?: String (default "recent")`, `category?: String`

## 2. 응답 (Response)
- 성공: `200 OK` + `DiaryDto.FeedResponse`

## 3. 비즈니스 로직 (요약)
1. `DiaryFeedQuery(cursor, size, sort=DiaryFeedSort.from(sort), tag=category)` 생성
2. `diaryFacade.getFeed(query, userId)` → 페이지 반환.

## 4. 데이터 의존
- DB read: diaries (공개 + 정렬·필터)

## 5. 예외 케이스
- 잘못된 sort 값 → `DiaryFeedSort.from(sort)` 처리에 따라 400 또는 fallback

## 6. 암묵적 로직 (Implicit)
- `category`가 내부적으로 `tag`로 매핑됨 — 외부 명칭과 내부 도메인 어휘 불일치(@FIX 후보).
- size 디폴트 10, max 미확인.

## 7. 호출자 (Clients)
- 모바일/웹 메인 피드

## 8. TODO / Open Questions
- [ ] sort 허용 값 ("recent", "popular" 등)
- [ ] category vs tag 통일

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: `category`/`tag` 명칭 통일 → `@FIX`
