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

## 9. KEEP/DROP/FIX 분류

**판정: FIX** (KEEP + 변경 사항 박제)

### KEEP 사유
- 일기 단건 조회 (상세 화면) 는 핵심 UX. viewer-context (좋아요 여부 / 댓글 수) 결합은 모바일 N+1 회피.

### FIX 항목

상세 박제: [`docs/decisions/diary/diary-domain-policy.md`](../../decisions/diary/diary-domain-policy.md).

1. **ID 타입 UUID** — `diaryId: Long` → `UUID` (path).
2. **응답 schema 11 필드 (create 와 동일)** — `diaryId / authorId / authorDisplayName / content / images / tags / visibility / likeCount / commentCount / likedByMe / createdAt`.
3. **viewer-context 포함** — `likedByMe` (호출자 기준), `commentCount` (denormalized 또는 read model). PRD §6 의 "userId 가 응답의 viewer-context 에 사용되는지" 부채 해소.
4. **비공개 일기 가드 — 404 (IDOR 보호)** — 비공개 + 비작성자 → 404. 403 미사용 (자원 존재 비노출). PRD §5 의 "403/404" 모호성 해소.
5. **조회수 미지원** — 조회수 카운터 / 응답 필드 미포함. 활동 / 인기도는 platform-service 의 `ActivityHappened` 이벤트 (PR #36 박제) 로 별도 트래킹. PRD §8 의 "조회수 증가 처리 위치" Open Question 해소.
6. **저장 (saved) 미지원** — PRD §6 의 "저장 상태 등" 언급은 본 시점 미반영 (Non-Goals — 저장 도메인 부재. 향후 도입 시 별도 결정).

### 영향 범위 (구현 PR 에서)
- diary-service: `GetDiaryService` + 비공개 가드 (작성자 == 호출자 또는 visibility=PUBLIC) + likedByMe 일괄 조회 + commentCount denormalized fetch + UserSummaryService gRPC.
- contracts: 변경 없음.
