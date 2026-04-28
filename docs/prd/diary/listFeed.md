---
api_id: diary.listFeed
http_method: GET
path: /api/v1/diaries/feed
auth: Y
controller: DiaryController.kt
handler: listFeed
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

## 9. KEEP/DROP/FIX 분류

**판정: FIX** (KEEP + 변경 사항 박제)

### KEEP 사유
- 공개 일기 피드는 본 서비스 메인 화면. cursor 페이징 + 정렬 / tag 필터는 표준 패턴.

### FIX 항목

상세 박제: [`docs/decisions/diary/diary-domain-policy.md`](../../decisions/diary/diary-domain-policy.md).

1. **카테고리 → 태그 명칭 통일 (tag 채택)** — query `category?: String` → `tag?: String` (PRD §6 의 "external `category` ↔ internal `tag`" 어휘 불일치 해소). 서버 내부 도메인도 `tag` 단일 어휘.
2. **size 상한 100** — `size?: Int (default 10, max 100)`. 100 초과 시 400.
3. **sort 허용 값** — `recent` (default, `created_at desc`) / `popular` (`like_count desc`, tiebreak `created_at desc`). 그 외 → 400. PRD §8 의 "sort 허용 값" Open Question 해소.
4. **cursor 형식** — `(sort_key, diary_id)` 조합 base64 opaque. sort 별 cursor 형식 다름 (recent: `(created_at, diary_id)` / popular: `(like_count, created_at, diary_id)`).
5. **공개 일기만 포함** — `visibility=PUBLIC` 만. 본인 비공개 일기는 `listMyFeed` 에서만 조회.
6. **응답 schema** — `FeedResponse { items: List<DiaryItem>, nextCursor: String?, hasNext: Boolean }`. `DiaryItem` 은 get 응답의 11 필드 중 content 부분 truncate (preview 용, 200자 max) — 또는 풀 content 반환. **결정**: 풀 content 반환 (UX — 클라가 자체 truncate, 서버 단순화). likedByMe / commentCount 일괄 조회.
7. **본인 일기 포함 여부** — 본인 공개 일기는 피드에 포함 (자신도 메인 피드 사용 시 본인 글 노출 자연). 비공개는 제외.

### 영향 범위 (구현 PR 에서)
- diary-service: `ListPublicFeedService` + sort 별 쿼리 분기 + cursor encoder/decoder + likedByMe / commentCount 일괄 조회 + `UserSummaryService.BatchGetUserSummaries` (최대 200, PR #35).
- contracts: 변경 없음.
