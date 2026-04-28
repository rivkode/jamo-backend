# Decision: diary 도메인 — 공개/비공개 / 응답 schema / 검증 흐름 / 페이징 / Saga cascade 정책

- **상태**: Accepted
- **결정일**: 2026-04-28
- **결정자**: jonghun
- **PR**: PR D-a-3 (`feature/diary-prd-evaluation-diary-core`)
- **관련 PRD**: [`prd/diary/create.md`](../../prd/diary/create.md), [`get.md`](../../prd/diary/get.md), [`listFeed.md`](../../prd/diary/listFeed.md), [`listMyFeed.md`](../../prd/diary/listMyFeed.md), [`delete.md`](../../prd/diary/delete.md), [`toggleLike.md`](../../prd/diary/toggleLike.md)
- **관련 결정**: [`decisions/diary/comment-domain-policy.md`](comment-domain-policy.md) (UUID / 404 통일 / 좋아요 멱등 일관), [`decisions/diary/validation-ai-fallback-policy.md`](validation-ai-fallback-policy.md) (검증 흐름)
- **관련 contracts**: `DiaryCreated` ✅ (PR #36 박제), `DiaryDeleted` 📝 **미정의 — 별도 contracts PR 로 박제 예정**, `UserSummaryService` (PR #35), `ActivityHappened` (PR #36)
- **관련 ADR**: [ADR-0007 contracts-first 병렬 개발](../../adr/0007-contracts-first-parallel-development.md)

## 컨텍스트

`diary/create · get · listFeed · listMyFeed · delete · toggleLike` 6 PRD 가 모두 `mined` 상태. diary 는 본 서비스의 중심 aggregate — 공개/비공개 정책, cascade (comment / diary_like / diarychat / sentence-feedback / platform 랭킹) 영향 범위가 가장 큼. 공통 정책을 단일 결정 로그로 박제.

이전 박제 (`comment-domain-policy.md`, `validation-ai-fallback-policy.md`) 와의 일관성 우선. comment 의 비공개 가드 / 404 통일 / 좋아요 멱등 정책은 본 결정의 visibility / 권한 / cascade 박제와 정합 검증.

## 결정

### 1. ID 타입 — UUID 일관

| 자원 | 타입 |
|---|---|
| `diaryId` | `UUID` (BINARY(16)) |
| `authorId` | `UUID` (identity-service 정합) |

profile (#39) / comment (#50) 평가 정합.

### 2. 공개/비공개 정책 — `Visibility` enum

```
enum Visibility {
  PUBLIC,    // 누구나 조회 (피드 노출, 비로그인 사용자도 — 단 본 시점 모든 endpoint auth=Y)
  PRIVATE,   // 작성자만 조회
  // FOLLOWERS_ONLY  ← 후속 (follow 도메인 도입 시)
}
```

| 시나리오 | HTTP |
|---|---|
| PUBLIC + 누구나 조회 (`get`, `listFeed`) | 200 |
| PRIVATE + 작성자 조회 | 200 |
| PRIVATE + 비작성자 조회 (`get`, `toggleLike`) | **404** (IDOR 보호, 403 미사용) |
| 비공개 + listFeed | 자동 제외 (`WHERE visibility = PUBLIC`) |
| 본인 listMyFeed | PUBLIC + PRIVATE 모두 포함 |

근거:
- `PUBLIC | PRIVATE` 만 시작. `FOLLOWERS_ONLY` 는 follow 도메인 미존재 → 후속 (Non-Goals).
- 404 통일은 comment-domain-policy §404 통일 정책과 동일 (자원 존재 비노출).

### 3. CreateRequest 필드 명시

```
CreateRequest {
  content: String          // 1..2000
  images: List<URL>        // max 5, 업로드는 별 endpoint (Non-Goals)
  tags: List<String>       // max 10, 각 1..30
  visibility: Visibility   // default PUBLIC
}
```

근거: PRD `create.md` §8 의 "텍스트 / 이미지 / 태그 / 공개 여부" Open Question 해소.

### 4. 응답 schema — `DiaryResponse` 11 필드

```
DiaryResponse {
  diaryId: UUID,
  authorId: UUID,
  authorDisplayName: String,    // UserSummaryService.GetUserSummary (단건) / BatchGetUserSummaries (목록)
  content: String,
  images: List<URL>,
  tags: List<String>,
  visibility: Visibility,
  likeCount: Int,
  commentCount: Int,
  likedByMe: Boolean,           // 호출자 기준
  createdAt: Instant
}
```

`get / create / listFeed / listMyFeed` 모두 동일 schema (목록은 `DiaryItem = DiaryResponse` 풀 content 반환). 작성 직후 likeCount=0 / commentCount=0 / likedByMe=false.

### 5. 검증 호출 순서 — 클라이언트 사전 호출 (선택적)

| 단계 | 책임 |
|---|---|
| 클라이언트 | `validate` / `validateLine` 으로 사전 검증 (선택적, validation-ai-fallback-policy 박제) |
| `create` (서버) | chat-service `ValidateDiaryContent` **재호출 X** (중복 비용 회피) |
| `create` (서버) | 도메인 invariant 만 검증 (content 길이 / 태그 개수 / images 개수) — 룰/LLM 검증 X |

근거:
- 서버 측 강제 검증 = LLM 비용 폭증 + Deadline 20s 가 작성 흐름 차단.
- 클라이언트가 사전 호출 후 통과한 결과를 신뢰 (validate 의 token 같은 검증 증명은 도입 안 함 — 단순화).
- 룰 위반 (offensive / banned word) 자체는 `Diary` aggregate 의 invariant 로 일부 차단 가능 (도메인 invariant) — 단 LLM 영역은 클라 책임.

PRD `create.md` §8 의 "검증 API 와의 호출 순서" Open Question 해소.

### 6. 태그 / 카테고리 명칭 통일 — `tag` 채택

PRD `listFeed.md` §6 의 "external `category` ↔ internal `tag`" 어휘 불일치 명시 + FIX 후보 표기. 본 결정으로:

- 외부 query 명: `category` → `tag`
- 내부 도메인: `tag` 단일 어휘
- 검색 / 필터: 단일 태그 매칭 (multi-tag intersection 후속)
- 정규화 / tag 도메인 분리 미적용 (free-form, 코드 단계 결정 보류)

### 7. 페이징 정책

| 항목 | listFeed | listMyFeed |
|---|---|---|
| `size` default | 10 | 10 |
| `size` max | 100 | 100 |
| 정렬 | `recent` (default) / `popular` | `recent` (only) |
| cursor | sort 별 형식 (recent: `(created_at, diary_id)`, popular: `(like_count, created_at, diary_id)`) base64 | `(created_at, diary_id)` base64 |
| visibility 필터 | `WHERE visibility=PUBLIC` | 무관 (작성자 본인) |

응답: `FeedResponse { items: List<DiaryItem>, nextCursor: String?, hasNext: Boolean }`.

본인 공개 일기는 listFeed 에 포함 (자기 글 노출 자연).

### 8. 좋아요 — comment 정합 (명시적 boolean 멱등)

| 항목 | 결정 |
|---|---|
| 핸들러 | `setLiked(diaryId, userId, liked: Boolean)` (PRD §6 기존 설계 유지) |
| 응답 | `{ diaryId, liked, likeCount }` |
| 자기 일기 좋아요 | 허용 (comment 와 동일) |
| 비공개 + 비작성자 | 404 |
| 멱등성 | `liked=true` UPSERT / `liked=false` DELETE (idempotent) |
| `DiaryLiked` 이벤트 | **미발행** (likeCount 가 popular 정렬에 즉시 반영, 이벤트 중복 회피) |
| `ActivityHappened` 이벤트 | 발행 X (좋아요는 가벼운 행위) — 단 정책 변경 시 본 결정 갱신 |

### 9. 삭제 — hard-delete + DiaryDeleted Saga cascade

| 항목 | 결정 |
|---|---|
| 삭제 방식 | hard-delete (comment 정합) |
| 권한 | 작성자 only |
| 응답 | `204 No Content` |
| 멱등성 | 비멱등 (이미 삭제 시 404) |
| diary-service 자체 트랜잭션 | `diaries` row 삭제 + Outbox `DiaryDeleted` insert |
| cascade | `DiaryDeleted` Kafka 이벤트 구독자가 비동기 처리 |
| 보상 트랜잭션 | best-effort (현재). 실패 모니터링 후속 |

**2PC / cross-service 트랜잭션 X** ([CLAUDE.md](../../../CLAUDE.md) 의 분산 트랜잭션 금지). Saga 패턴.

#### DiaryDeleted 구독자 cascade 명세 (각 도메인 평가 시점 정합)

| 구독자 | 동작 | 평가 시점 |
|---|---|---|
| diary-service 자체 | `comments` hard-delete cascade + `diary_likes` hard-delete | comment 평가 #50 박제 |
| platform-service | 활동 점수 차감 (또는 음수 `ActivityHappened` 보상 발행) | platform 평가 시점 |
| chat-service | 해당 일기의 `diarychat` 종료 + 메시지 보존 / 또는 일괄 삭제 | diarychat 평가 D-a-4 시점 정합 |
| learning-service | sentence-feedback 정리 | D-a-5 시점 정합 |

각 구독자는 `ProcessedEvent` 멱등 처리 ([CLAUDE.md](../../../CLAUDE.md) Kafka Consumer 멱등성 의무).

### 10. DiaryDeleted contracts 미정의 — 별도 contracts PR

[`contracts-catalog.md`](../../architecture/contracts-catalog.md) §도메인 이벤트(diary):

| 이벤트 | 상태 |
|---|---|
| `DiaryCreated` | ✅ 등재 (PR #36) |
| `DiaryDeleted` | 📝 **미작성 (diary 도메인 PR)** |
| `CommentCreated` | ✅ 등재 (PR #36) |
| `SentenceFeedbackRequested` / `SentenceFeedbackAccepted` / `SentenceFeedbackRejected` | 📝 미작성 (sentence-feedback PR) |

본 PR (D-a-3 평가) 시점 **DiaryDeleted record 박제하지 않음** (사용자 정책 "선행 필요만 기록"). 별도 contracts PR 에서:
- `DiaryDeleted` record 신설 (`eventId / occurredAt / diaryId / authorId`)
- `contracts-catalog.md` `DiaryDeleted` 행 ✅ 전환
- (선택) 같은 PR 에 `SentenceFeedback*` 3종도 일괄 박제 — D-a-5 시점 결정

### 11. 조회수 — 미지원 (Non-Goals)

| 항목 | 결정 |
|---|---|
| `views` 카운터 | 미지원 |
| 응답 필드 | 미포함 |
| 활동 / 인기도 | platform-service `ActivityHappened` 이벤트 (PR #36) 로 별도 트래킹 |

PRD `get.md` §8 의 "조회수 증가 처리 위치" Open Question 해소.

### 12. 저장 (saved) — 미지원 (Non-Goals)

PRD `get.md` §6 의 "저장 상태 등" 언급은 본 시점 미반영. 향후 save 도메인 도입 시 별도 결정.

### 13. 알림 정책 — 후속 (Non-Goals)

- `DiaryCreated` 의 follower 알림은 notification 도메인 도입 시 박제.
- 좋아요 / 댓글 알림은 comment-domain-policy 와 동일 — 후속.

## 검토한 옵션 (요약)

### Option A. soft-delete (`deleted_at` 컬럼) — 거부
- comment 와 정책 불일치 (comment 는 hard-delete 채택).
- cascade 시 deleted_at 전파 부담.

### Option B. visibility = `PUBLIC | PRIVATE | FOLLOWERS_ONLY` 시작 시점 도입 — 거부
- follow 도메인 미존재 → FOLLOWERS_ONLY 의미 불완전.
- enum 값 추가는 후속 가능 (proto enum 미사용, schema 호환).

### Option C. 서버 측 강제 검증 (create 시 ValidateDiaryContent 자동 호출) — 거부
- LLM 비용 폭증 (모든 작성에 호출).
- Deadline 20s 가 작성 흐름 차단 — UX 손실.
- 클라 사전 호출 + UX 가이드로 충분.

### Option D. visibility 가드 — 403 (Forbidden) — 거부
- IDOR 위험 (자원 존재 노출).
- comment-domain-policy §404 통일 정책과 일관 어려움.

### Option E. cascade — 동기 트랜잭션 (cross-service 2PC) — 거부
- [CLAUDE.md](../../../CLAUDE.md) 분산 트랜잭션 금지.
- 가용성 / latency 부담.
- Saga + 보상 트랜잭션이 표준 ([Microservices Patterns](https://microservices.io/patterns/data/saga.html)).

### Option F. cascade — 동기 적시 삭제 (DiaryDeleted 발행 X, 직접 호출) — 거부
- 서비스간 강결합.
- 트랜잭션 일관성 확보 어려움.
- gRPC 호출 실패 시 부분 삭제.

### Option G. 풀 content 반환 vs preview truncate — 풀 content 채택
- 클라가 자체 truncate (UI 결정).
- 서버 단순화.
- 이미지 썸네일은 별도 (이미지 업로드 도메인 후속).

## 결과 및 영향

### 즉시
- 6 PRD §9 채움 (모두 KEEP+FIX, 본 결정 박제 cross-reference).
- comment-domain-policy 의 "비공개 일기 가드 (D-a-3 박제 후 정합 검증)" 후속 의무 해소 — 비공개 + 비작성자 → 404 통일 박제 완료.

### 후속 (구현 PR 시점)

**별도 contracts PR (선행 필요)**:
- `DiaryDeleted` Kafka 이벤트 record 신설 (D-a-5 의 SentenceFeedback* 와 일괄 박제 권장).
- `contracts-catalog.md` 갱신.

**diary-service**:
- `Diary` aggregate (`DiaryContent` VO + `Tag` VO + `Visibility` enum + invariant: content 1..2000 / images max 5 / tags max 10 / 각 tag 1..30).
- `DiaryRepository` + `DiaryLikeRepository`.
- Application Services 6종: `CreateDiaryService` / `GetDiaryService` / `ListPublicFeedService` / `ListMyFeedService` / `DeleteDiaryService` / `ToggleDiaryLikeService`.
- `DiaryController` (`POST /diaries`, `GET /diaries/{diaryId}`, `GET /diaries/feed`, `GET /diaries/me`, `DELETE /diaries/{diaryId}`).
- `DiaryLikeController` (`POST /diaries/{diaryId}/like`).
- `DiaryExceptionHandler` (404 통일).
- Outbox 패턴 어댑터 (DiaryCreated / DiaryDeleted 발행).
- `UserSummaryService` gRPC client (Get 단건 / Batch 일괄).
- `DiaryDeleted` 이벤트 핸들러 (자체 cascade — comments / diary_likes hard-delete).
- Flyway migration (`diaries`, `diary_likes`, `outbox_event` 활용 / 인덱스).

**다른 서비스 (DiaryDeleted 구독자)**:
- chat-service (D-a-4 정합)
- learning-service (D-a-5 정합)
- platform-service (랭킹 차감)

### 결정 대기 (본 결정에서 다루지 않음)
- 이미지 업로드 endpoint — 별도 PR.
- save / bookmark 도메인 — 별도 PR.
- follow / FOLLOWERS_ONLY visibility — 별도 PR.
- multi-tag intersection 검색 — 후속.
- popular sort 의 시간 가중치 (week / month) — 코드 슬라이스 시점.
- `diaries.like_count` / `comment_count` denormalized vs read model — 코드 슬라이스 시점.
- DiaryDeleted 구독자 부분 실패 시 보상 트랜잭션 / 모니터링 — 운영 PR.
- moderation / 신고 / 강제 삭제 — moderation 도메인 후속.
- `DiaryLiked` / `ActivityHappened` 발행 정책 변경 — 운영 모니터링 후 결정.

### Non-Goals
- 조회수 카운터 / 응답 필드.
- 저장 / 북마크.
- 강제 삭제 (moderation).
- 알림 (notification 도메인).
- soft-delete / undelete.
- DiaryLiked / 좋아요 알림 이벤트.
- multi-tag 검색.
- 이미지 직접 업로드 (현 시점 URL 만 받음).
- Saga 보상 트랜잭션 자동화 (현재 best-effort).

## 참고

- [`prd/diary/*.md`](../../prd/diary/) §9 (6 PRD)
- [`decisions/diary/comment-domain-policy.md`](comment-domain-policy.md) — 404 통일 / 좋아요 멱등 / hard-delete cascade 일관 선례
- [`decisions/diary/validation-ai-fallback-policy.md`](validation-ai-fallback-policy.md) — 검증 흐름 / chat-service 호출 정책
- [`decisions/identity/profile-prd-evaluation.md`](../identity/profile-prd-evaluation.md) — UUID 전환 패턴 선례
- [`docs/architecture/contracts-catalog.md`](../../architecture/contracts-catalog.md) — DiaryDeleted 미작성 표기 / DiaryCreated 박제
- [ADR-0007 contracts-first 병렬 개발](../../adr/0007-contracts-first-parallel-development.md)
- [CLAUDE.md](../../../CLAUDE.md) — 분산 트랜잭션 금지 / Outbox 의무 / Kafka Consumer 멱등성 의무 / 404 통일 IDOR 보호
- Chris Richardson, *Microservices Patterns* (Saga, Outbox)
