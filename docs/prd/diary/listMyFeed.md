---
api_id: diary.listMyFeed
http_method: GET
path: /api/v1/diaries/me
auth: Y
controller: DiaryController.kt
handler: listMyFeed
status: mined
---

# GET /api/v1/diaries/me — 내 일기 목록

## 1. 요청 (Request)
- Header: `@LoginUser`
- Query: `cursor?: String`, `size?: Int (default 10)`

## 2. 응답 (Response)
- 성공: `200 OK` + `DiaryDto.FeedResponse`

## 3. 비즈니스 로직 (요약)
1. `diaryFacade.getMyFeed(userId, cursor, size)` → 사용자 본인 일기 페이지.

## 4. 데이터 의존
- DB read: diaries (user_id 필터)

## 5. 예외 케이스
- 인증 실패 → 401

## 6. 암묵적 로직 (Implicit)
- public/private 모두 포함 추정 (본인 조회).

## 7. 호출자 (Clients)
- 모바일/웹 (마이페이지)

## 8. TODO / Open Questions
- [ ] 정렬 옵션 부재 — 항상 최신순 가정?

## 9. KEEP/DROP/FIX 분류

**판정: FIX** (KEEP + 변경 사항 박제)

### KEEP 사유
- 마이페이지 본인 일기 목록은 핵심 UX. 본인은 public + private 모두 조회 가능.

### FIX 항목

상세 박제: [`docs/decisions/diary/diary-domain-policy.md`](../../decisions/diary/diary-domain-policy.md).

1. **size 상한 100** — `size?: Int (default 10, max 100)`. listFeed 와 동일.
2. **정렬 — recent only** — `created_at desc` 단일. listFeed 의 popular 정렬 미지원 (마이페이지 UX 는 시간순 자연). 정렬 옵션 query 미노출 (PRD §8 의 "정렬 옵션 부재" 명시 채택).
3. **public + private 모두 포함** — 작성자 본인이라 visibility 무관. PRD §6 의 "public/private 모두 포함 추정" 박제.
4. **cursor 형식** — `(created_at, diary_id)` base64 opaque (recent 단일).
5. **응답 schema** — listFeed 와 동일 `FeedResponse`. `DiaryItem` 11 필드 + visibility 노출 (본인이 자기 일기 공개 여부 확인용).
6. **likedByMe** — 본인이 자기 일기에 좋아요 가능 (자기 좋아요 허용 정책, comment 와 동일).

### 영향 범위 (구현 PR 에서)
- diary-service: `ListMyFeedService` + `WHERE author_id = userId` (visibility 무관) + cursor + likedByMe 일괄 조회.
- contracts: 변경 없음.
