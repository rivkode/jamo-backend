---
api_id: comment.create
http_method: POST
path: /api/v1/diaries/{diaryId}/comments
auth: Y
controller: DiaryCommentController.kt
handler: create
status: mined
---

# POST /api/v1/diaries/{diaryId}/comments — 일기 댓글 작성

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `diaryId: Long`
- Body: `CommentDto.CreateRequest` (`@Valid`)

## 2. 응답 (Response)
- 성공: `201 Created` + `CommentDto.CommentResponse`

## 3. 비즈니스 로직 (요약)
1. `request.toCommand(diaryId, userId)` → `commentFacade.create(...)` → 저장.

## 4. 데이터 의존
- DB write: comments

## 5. 예외 케이스
- validation → 400
- 일기 없음 → 404

## 6. 암묵적 로직 (Implicit)
- 답글(parentId) 지원 여부는 Request DTO 확인 필요.
- 알림(notification) 발송 트리거 가능성 (이벤트 발행 추정).

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 답글 깊이 제한
- [ ] 알림 발송 정책

## 9. KEEP/DROP/FIX 분류

**판정: FIX** (KEEP + 변경 사항 박제)

### KEEP 사유
- 일기에 대한 댓글 작성은 핵심 소셜 UX. Kafka `CommentCreated` 이벤트는 contracts (PR #36) 박제 완료 — 발행자 = diary-service, 구독자 = platform (랭킹).

### FIX 항목

상세 박제: [`docs/decisions/diary/comment-domain-policy.md`](../../decisions/diary/comment-domain-policy.md).

1. **ID 타입 UUID 전환** — `diaryId: Long` → `UUID` (path), 응답의 `commentId`, `userId` 모두 UUID. profile 평가 (#39) 의 정합 결정 패턴 일관.
2. **답글 깊이 1단 제한** — `CreateRequest` 에 `parentId: UUID?` (nullable) 추가. parent 가 답글 (parent.parentId != null) 이면 400 — 깊이 1단 (parent → child only). UX 단순화 + 운영 부담 감소.
3. **응답 schema 명시화** — `CommentResponse` 가 `commentId / diaryId / authorId / authorDisplayName / content / parentId / likeCount / likedByMe / createdAt` 9 필드 (likeCount=0, likedByMe=false 초기값). authorDisplayName 은 `UserSummaryService` (PR #35) gRPC 호출로 조립.
4. **CommentCreated 이벤트 발행 명시** — §3 비즈니스 로직에 Outbox 패턴 명시: 동일 트랜잭션에서 `outbox_event` 테이블 insert → 비동기 발행자가 Kafka `diary-events` 토픽 publish. `eventId` 는 멱등성 키, 구독자 (platform) 는 `ProcessedEvent` 멱등 처리 ([CLAUDE.md](../../../CLAUDE.md) 의 Kafka Consumer 멱등성 의무).
5. **일기 비공개 가드** — 비공개 일기에 댓글 작성 시 404 (IDOR 보호). 본 정책은 diary 평가 (D-a-3) 의 비공개 정책 박제 후 정합 검증 — 본 PR 시점은 "공개 일기 가정" + 비공개 도입 시 가드 자동 적용.
6. **알림 정책** — `CommentCreated` 이벤트 구독 시 platform-service (랭킹) 외에 알림 시스템은 별도 (notification 도메인 미정의). 현재는 platform 만, 알림은 후속 (Non-Goals).
7. **Open Questions 해소** — §8 답글 깊이 → 1단 / 알림 → 후속.

### 영향 범위 (구현 PR 에서)
- diary-service: `Comment` aggregate (parentId VO + 깊이 검증 invariant) + `CommentRepository` + `CreateCommentService` (Outbox + UserSummary gRPC) + `DiaryCommentController`.
- contracts: 변경 없음 (`CommentCreated` 이미 박제).
