# Decision: comment 도메인 — ID 타입 / 답글 / 삭제 / 권한 / 페이징 / 좋아요 정책

- **상태**: Accepted
- **결정일**: 2026-04-28
- **결정자**: jonghun
- **PR**: PR D-a-2 (`feature/diary-prd-evaluation-comment`)
- **관련 PRD**: [`prd/comment/create.md`](../../prd/comment/create.md), [`prd/comment/list.md`](../../prd/comment/list.md), [`prd/comment/delete.md`](../../prd/comment/delete.md), [`prd/comment/toggleLike.md`](../../prd/comment/toggleLike.md)
- **관련 결정**: [`decisions/identity/profile-prd-evaluation.md`](../identity/profile-prd-evaluation.md) (UUID 전환 패턴 일관)
- **관련 contracts**: `CommentCreated` Kafka 이벤트 (PR #36 박제), [`UserSummaryService` (PR #35)](../contracts/) (BatchGetUserSummaries 일괄 조회)

## 컨텍스트

`comment/create · list · delete · toggleLike` 4 PRD 가 모두 `mined` 상태. 4건 평가 시 ID 타입 / 권한 / 비공개 일기 가드 / cursor 페이징 / 답글 깊이 / soft vs hard delete 등 정책이 공통으로 영향을 끼치므로 단일 결정 로그로 박제.

`comment` 도메인은 diary-service 안의 sub-도메인이며 AI 의존 없음. Kafka `CommentCreated` 이벤트의 발행자 (구독자 = platform-service 랭킹).

## 결정

### 1. ID 타입 — UUID 일관

| 자원 | 타입 |
|---|---|
| `commentId` | `UUID` (BINARY(16) MySQL) |
| `diaryId` | `UUID` (path) |
| `userId` (author) | `UUID` (identity-service 정합) |
| `parentId` | `UUID?` (nullable) |

profile 평가 (#39 [`profile-prd-evaluation.md`](../identity/profile-prd-evaluation.md)) 의 `userId Long → UUID` 결정 패턴 일관. mined PRD 의 `Long` 표기를 모두 UUID 로 통일.

### 2. 답글 깊이 1단 제한

`Comment` aggregate 가 `parentId: UUID?` 보유 (NULL = 루트, 값 = 1단 답글). `parent.parentId != null` 인 댓글에 답글 시도 시 400.

| 시도 | 결과 |
|---|---|
| 루트 댓글 작성 (`parentId=null`) | 200 |
| 답글 작성 (`parentId=루트댓글id`) | 200 |
| 답글에 답글 시도 (`parentId=답글id`) | 400 (`parent_must_be_root`) |

근거:
- UX 단순화 — 무한 깊이 시 모바일 화면 가독성 저하.
- 운영 부담 감소 — cascade 깊이 1로 한정, list 트리 구성 단순화.
- Twitter / Instagram 댓글 패턴과 일치.

### 3. 삭제 정책 — hard-delete + cascade

| 항목 | 결정 |
|---|---|
| 댓글 삭제 | hard-delete |
| 답글 처리 | parent 삭제 시 자식 답글도 hard-delete (cascade) |
| 권한 | 작성자 only |
| 일기 작성자 강제 삭제 | **미부여** (신고 시스템 후속) |
| 응답 | `204 No Content` |
| 멱등성 | 비멱등 — 이미 삭제 시 404 |

근거:
- 사용자 의도 명확 (회수). soft-delete 의 "삭제됐어요" 표시는 UX 손상.
- GDPR / 개인정보 친화 — 사용자 데이터 자기 통제.
- 운영 단순화 (deleted_at 필터 부담 X).
- depth 1단 제한이라 cascade 단순.
- 일기 작성자 강제 삭제 권한은 운영 정책 / 신고 시스템 영역. 본 PR 미포함 ([Non-Goals](#non-goals)).

### 4. 404 통일 (IDOR 보호)

| 시나리오 | HTTP |
|---|---|
| 댓글 없음 | 404 |
| 작성자 아님 (delete) | 404 (403 미사용) |
| 비공개 일기 댓글 list / toggleLike | 404 |
| 답글에 답글 시도 | 400 (`parent_must_be_root`) |

근거:
- 403 (Forbidden) 은 자원 존재 노출 — IDOR 공격에 활용 가능.
- 404 통일 시 작성자가 아닌 자가 commentId 추측해도 존재 여부 비노출.

### 5. 비공개 일기 가드

비공개 일기 (diary 평가 D-a-3 시점 박제 예정) 의 댓글에 대한 모든 endpoint (`list`, `delete`, `toggleLike`) 가 404 반환. `create` 도 동일 (비공개 일기의 댓글 작성 차단).

본 PR 시점은 **"공개 일기 가정"** + 비공개 도입 시 가드 자동 적용. 정합 검증은 D-a-3 박제 후 retro.

### 6. 페이징 (list)

| 항목 | 값 |
|---|---|
| `size` default | 20 |
| `size` max | 100 |
| 정렬 | 작성일 오름차순 (chronological — 대화 순서) |
| cursor | `(created_at, comment_id)` 조합 base64 encode (opaque) |
| 응답 | `items` + `nextCursor: String?` + `hasNext: Boolean` |

근거:
- 일반 댓글 UX 는 chronological (대화 순서 유지). 인기순 / 최신순은 후속.
- cursor opaque 로 서버 구현 변경 자유도 보장.

### 7. 응답 schema — 9 필드 통일

`CommentResponse` (create / list 응답 항목 동일):

```
{
  commentId: UUID,
  diaryId: UUID,
  authorId: UUID,
  authorDisplayName: String,    // UserSummaryService 조립
  content: String,
  parentId: UUID?,              // null = 루트 댓글
  likeCount: Int,
  likedByMe: Boolean,           // 호출자 기준
  createdAt: Instant
}
```

list 응답 항목별로 `UserSummaryService.BatchGetUserSummaries` (PR #35 박제, 최대 200) 일괄 호출로 displayName 조립. likedByMe 는 `comment_likes` 일괄 조회 후 Set<commentId> 매핑.

### 8. 좋아요 — 명시적 boolean 멱등 설계 유지

PRD `toggleLike.md` §6 의 "토글이라는 핸들러명이지만 클라이언트가 명시적으로 `liked: true/false` 보냄" 패턴 유지 (이미 좋은 설계).

| 항목 | 결정 |
|---|---|
| 핸들러 | `setLiked(commentId, userId, liked: Boolean)` |
| 응답 | `{ commentId, liked, likeCount }` |
| 자기 좋아요 | 허용 (별도 검증 없음) |
| 멱등성 | `liked=true` UPSERT / `liked=false` DELETE — 클라이언트 retry 안전 |
| likeCount 동기화 | `comments.like_count` denormalized 또는 read model (코드 슬라이스 시점 결정) |
| `CommentLiked` 이벤트 | **미발행** — 알림 도메인 도입 시 박제 (현재 Non-Goals) |

### 9. CommentCreated 이벤트 — Outbox 패턴

contracts `CommentCreated` (PR #36 박제) 발행:

```
diary-service create 트랜잭션 {
  INSERT INTO comments ...
  INSERT INTO outbox_event (event_type='CommentCreated', payload, ...)
}
                        ↓ commit
비동기 발행자: outbox → Kafka diary-events 토픽
                        ↓
platform-service: ProcessedEvent 멱등 → 활동 점수 가산
```

`eventId` (UUID) 가 멱등성 키. 구독자는 `ProcessedEvent` 테이블로 중복 처리 차단 ([CLAUDE.md](../../../CLAUDE.md) 의 Kafka Consumer 멱등성 의무).

### 10. 알림 정책 — 후속 (Non-Goals)

- 일기 작성자에게 댓글 알림 / 댓글 작성자에게 답글 알림 / 좋아요 알림은 **notification 도메인** 도입 시 박제.
- 본 PR 시점 알림 미발송. PRD §6 의 "알림 발송 트리거 가능성 (이벤트 발행 추정)" 부채는 후속으로 명시 박제.

## 미정의 contracts (선행 필요)

- **`DiaryDeleted` Kafka 이벤트** (PR #36 미정의, [contracts-catalog](../../architecture/contracts-catalog.md) §도메인 이벤트(diary) "📝 미작성 (diary 도메인 PR)") — 일기 삭제 시 댓글 cascade 영향. **PR D-a-3 (diary 평가) 시점에 박제 권장**. 본 PR 본문에 "선행 필요" 만 기록.

## 검토한 옵션 (요약)

### Option A. soft-delete (deleted_at 컬럼) — 거부
- "삭제된 댓글입니다" 표시는 UX 손상. 일반 사용자 댓글에는 부적절.
- 답글 cascade 시 deleted_at 전파 부담.
- 운영 / GDPR 부담.

### Option B. 답글 무한 깊이 — 거부
- 모바일 가독성 저하 (들여쓰기 한계).
- cascade 복잡도 ↑.

### Option C. 답글 미지원 (flat 만) — 거부
- 일반 댓글 UX 의 답글 기대치 미달.

### Option D. 좋아요 토글 (단순 POST, body 없음) — 거부
- 클라이언트 retry 시 의도치 않은 toggle. 명시적 `liked: Boolean` 멱등 설계 우월.

### Option E. 일기 작성자 댓글 강제 삭제 권한 — 보류
- 신고 시스템 / moderation 도메인의 영역. 별도 PR / 결정 로그로 분리.

## 결과 및 영향

### 즉시
- 4 PRD §9 채움 (모두 KEEP+FIX, 본 결정 박제 cross-reference).

### 후속 (구현 PR 시점)

**diary-service**:
- `Comment` aggregate (`parentId: UUID?`, depth 1단 invariant).
- `CommentLike` Entity (`(commentId, userId)` 유니크).
- `CommentRepository` + `CommentLikeRepository`.
- Application Services 4종: `CreateCommentService` / `ListCommentsService` / `DeleteCommentService` / `ToggleCommentLikeService`.
- `DiaryCommentController` (`POST /diaries/{diaryId}/comments`, `GET /diaries/{diaryId}/comments`).
- `CommentController` (`DELETE /comments/{commentId}`, `POST /comments/{commentId}/like`).
- `CommentExceptionHandler` (404 통일).
- Outbox 패턴 어댑터 (CommentCreated 발행).
- `UserSummaryService` gRPC client (BatchGetUserSummaries 호출).
- Flyway migration (`comments`, `comment_likes`, `outbox_event`, indexes).

**contracts**: 변경 없음 (`CommentCreated` 이미 박제).

### 결정 대기 (본 결정에서 다루지 않음)
- `comments.like_count` denormalized vs read model — 코드 슬라이스 시점 결정.
- displayName cache (Redis) vs 매번 gRPC — 코드 슬라이스 시점.
- 신고 시스템 / moderation 도메인 — 별도 PR.
- notification 도메인 — 별도 PR.
- `DiaryDeleted` 이벤트 명세 — PR D-a-3 박제.

### Non-Goals
- 일기 작성자의 댓글 강제 삭제 권한 — moderation 도메인.
- 댓글 알림 / 좋아요 알림 — notification 도메인.
- 댓글 신고 — moderation 도메인.
- 인기순 댓글 정렬 — 후속 (chronological 만).
- soft-delete 복원 (undelete) — 미지원.
- `CommentLiked` 이벤트 — 알림 도메인 도입 시.

## 참고

- [`prd/comment/create.md`](../../prd/comment/create.md) §9
- [`prd/comment/list.md`](../../prd/comment/list.md) §9
- [`prd/comment/delete.md`](../../prd/comment/delete.md) §9
- [`prd/comment/toggleLike.md`](../../prd/comment/toggleLike.md) §9
- [`decisions/identity/profile-prd-evaluation.md`](../identity/profile-prd-evaluation.md) — UUID 전환 패턴 선례
- [`docs/architecture/contracts-catalog.md`](../../architecture/contracts-catalog.md) — `CommentCreated` 박제 / `DiaryDeleted` 미작성 표기
- [ADR-0007 contracts-first 병렬 개발](../../adr/0007-contracts-first-parallel-development.md)
- [CLAUDE.md](../../../CLAUDE.md) — Kafka Consumer 멱등성 의무, Outbox 의무
