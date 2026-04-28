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

## 9. KEEP/DROP/FIX 분류

**판정: FIX** (KEEP + 변경 사항 박제)

### KEEP 사유
- 일기 좋아요는 핵심 소셜 인터랙션. **명시적 boolean 멱등 설계** (PRD §6) 가 이미 좋음 — comment.toggleLike 와 동일 패턴.

### FIX 항목

상세 박제: [`docs/decisions/diary/diary-domain-policy.md`](../../decisions/diary/diary-domain-policy.md).

1. **ID 타입 UUID** — `diaryId: Long` → `UUID`.
2. **응답 likeCount 포함** — `ToggleResponse { diaryId, liked, likeCount }`. PRD §8 의 "좋아요 수 응답 포함 여부" Open Question 해소.
3. **자기 일기 좋아요 허용** — comment 와 동일 정책 (UX 자유도).
4. **비공개 일기 가드 — 404** — 비공개 + 비작성자 → 404. 작성자 본인은 자기 비공개 일기에 좋아요 가능 (의미는 약하지만 일관성).
5. **일기 없음 → 404** — 멱등성 손상 없음.
6. **likeCount 동기화** — `(diaryId, userId)` 유니크 제약 + idempotent UPSERT/DELETE. `diaries.like_count` denormalized 또는 read model — 코드 슬라이스 시점 결정.
7. **DiaryLiked 이벤트** — 알림 / 인기도 트래킹용 이벤트 미발행 (현재 Non-Goals). platform 랭킹은 별도 활동 이벤트 (`ActivityHappened`) 로 처리 — 단 본 endpoint 자체는 ActivityHappened 발행 여부 결정 필요. **본 시점 결정**: 발행 X (likeCount 가 read model 의 popular 정렬에 즉시 반영, 이벤트 중복 회피).

### 영향 범위 (구현 PR 에서)
- diary-service: `DiaryLike` Entity + `ToggleDiaryLikeService` + idempotent UPSERT/DELETE + likeCount 동기화.
- contracts: 변경 없음.
