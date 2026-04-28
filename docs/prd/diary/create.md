---
api_id: diary.create
http_method: POST
path: /api/v1/diaries
auth: Y
controller: DiaryController.kt
handler: create
status: mined
---

# POST /api/v1/diaries — 일기 작성

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: `DiaryDto.CreateRequest` (`@Valid`)

## 2. 응답 (Response)
- 성공: `201 Created` + `DiaryDto.DiaryResponse`

## 3. 비즈니스 로직 (요약)
1. `request.toCommand(userId)` → `diaryFacade.create(...)` → 저장.

## 4. 데이터 의존
- DB write: diaries
- Kafka: 일기 생성 이벤트 발행 가능성 (확인 필요)

## 5. 예외 케이스
- validation 실패 → 400

## 6. 암묵적 로직 (Implicit)
- 검증 결과(영역 validation)를 사전에 받아두고 만드는 흐름인지 확인 필요.
- 태그/카테고리 연관 처리 위치.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] CreateRequest 필드 (텍스트, 이미지, 태그, 공개 여부)
- [ ] 검증 API와의 호출 순서

## 9. KEEP/DROP/FIX 분류

**판정: FIX** (KEEP + 변경 사항 박제)

### KEEP 사유
- 일기 작성은 본 서비스의 핵심 행위. Kafka `DiaryCreated` 이벤트는 contracts (PR #36) 박제 완료 — 발행자 = diary-service, 구독자 = platform (랭킹).

### FIX 항목

상세 박제: [`docs/decisions/diary/diary-domain-policy.md`](../../decisions/diary/diary-domain-policy.md).

1. **ID 타입 UUID** — 응답의 `diaryId / authorId` UUID. profile / comment 평가 정합.
2. **CreateRequest 필드 명시** — `content: String (1..2000)`, `images: List<URL> (max 5)`, `tags: List<String> (max 10, 각 1..30)`, `visibility: PUBLIC | PRIVATE (default PUBLIC)`. PRD §8 의 "텍스트 / 이미지 / 태그 / 공개 여부" Open Question 해소. 이미지 업로드는 별도 업로드 endpoint 후속 (Non-Goals).
3. **응답 schema 11 필드** — `diaryId / authorId / authorDisplayName / content / images / tags / visibility / likeCount / commentCount / likedByMe / createdAt`. 작성 직후 likeCount=0, commentCount=0, likedByMe=false. authorDisplayName 은 `UserSummaryService.GetUserSummary` (PR #35) gRPC 호출.
4. **검증 호출 순서: 클라이언트 사전 호출 (선택적)** — `validate` / `validateLine` 은 클라가 작성 도중 / 제출 직전 호출. `create` 는 chat-service 의 `ValidateDiaryContent` 를 **다시 호출하지 않음** (중복 비용 회피, 클라 측 사전 호출 가정). 룰 위반은 서버측 도메인 invariant (offensive 차단) 만 검증. PRD §8 의 "검증 API 와의 호출 순서" Open Question 해소.
5. **DiaryCreated Outbox 발행** — 동일 트랜잭션에서 `outbox_event` insert → 비동기 발행자가 Kafka `diary-events` 토픽 publish. `eventId` 멱등성 키, 구독자 (platform) 의 `ProcessedEvent` 중복 차단.
6. **태그 처리** — 태그는 자유 입력 (별도 tag 도메인 / 정규화 미적용). 검색 / 필터는 listFeed `tag` query (단일 태그 매칭).
7. **알림 정책** — `DiaryCreated` 의 follower 알림은 notification 도메인 도입 시 후속 (Non-Goals). 본 endpoint 자체는 platform 랭킹만 영향.
8. **카테고리 → 태그 통일** — listFeed `category` query 와의 명칭 충돌은 본 도메인 정책 박제 §6 (`tag` 채택) 으로 일괄 해소.

### 영향 범위 (구현 PR 에서)
- diary-service: `Diary` aggregate (DiaryContent VO + Tag VO + Visibility enum + invariant) + `DiaryRepository` + `CreateDiaryService` (Outbox + UserSummary gRPC) + `DiaryController`.
- contracts: 변경 없음 (`DiaryCreated` 이미 박제).
