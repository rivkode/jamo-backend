---
api_id: comment.list
http_method: GET
path: /api/v1/diaries/{diaryId}/comments
auth: Y
controller: DiaryCommentController.kt
handler: list
status: mined
---

# GET /api/v1/diaries/{diaryId}/comments — 일기 댓글 목록 (cursor 페이징)

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `diaryId: Long`
- Query: `cursor?: String`, `size?: Int (default 20)`

## 2. 응답 (Response)
- 성공: `200 OK` + `CommentDto.CommentListResponse`

## 3. 비즈니스 로직 (요약)
1. `commentFacade.list(diaryId, cursor, size, userId)` → cursor 페이지 응답.

## 4. 데이터 의존
- DB read: comments (diary_id 필터, cursor 기반)

## 5. 예외 케이스
- 인증 실패 → 401
- 비공개 일기 접근 → 403/404 (Facade 내부 가드)

## 6. 암묵적 로직 (Implicit)
- size 디폴트 20, max 미확인 — 클라이언트가 큰 값 전달 시 가드 필요.
- cursor 형식(opaque string) — Facade 책임.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] size 상한
- [ ] 정렬 기준(작성일 내림차순 등)

## 9. KEEP/DROP/FIX 분류

**판정: FIX** (KEEP + 변경 사항 박제)

### KEEP 사유
- 일기 상세 화면에서 댓글 목록 조회는 필수. cursor 페이징은 무한 스크롤 UX 와 정합.

### FIX 항목

상세 박제: [`docs/decisions/diary/comment-domain-policy.md`](../../decisions/diary/comment-domain-policy.md).

1. **ID 타입 UUID 전환** — `diaryId: Long` → `UUID` (path).
2. **size 상한 100** — query `size?: Int (default 20, max 100)`. 100 초과 시 400.
3. **정렬: 작성일 오름차순 (chronological)** — 일반 댓글 UX (대화 순서). cursor 는 `(created_at, comment_id)` 조합 base64 encode.
4. **비공개 일기 가드 — 404 (보안)** — 비공개 일기 접근 시 403 (존재 노출) 대신 404 (존재 비노출). PRD §5 의 "403/404" 모호성 해소. diary 평가 (D-a-3) 의 비공개 정책 박제 후 정합 검증.
5. **응답 schema 명시화** — `CommentListResponse` 가 `items: List<CommentItem>` + `nextCursor: String?` + `hasNext: Boolean`. 각 `CommentItem` 은 `create.md` §9 의 `CommentResponse` 9 필드 (likedByMe 는 호출자 기준).
6. **답글 표현** — flat list (parentId 포함) 반환. 클라이언트가 parentId 로 트리 구성. 서버측 트리 구조 미반환 (응답 단순화 + 페이징 정합).
7. **likeCount + likedByMe 동시 반환** — like 도메인 호출 (Comment ↔ CommentLike join) 또는 read model. 구현 결정은 D-a-2 코드 슬라이스 시점.

### 영향 범위 (구현 PR 에서)
- diary-service: `ListCommentsService` + cursor encoder/decoder + `UserSummaryService` 일괄 호출 (`BatchGetUserSummaries` 최대 200, [identity.proto](../../decisions/contracts/) PR #35 박제) + likedByMe 계산 (Set<commentId> 일괄 조회).
- contracts: 변경 없음.
