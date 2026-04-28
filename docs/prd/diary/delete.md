---
api_id: diary.delete
http_method: DELETE
path: /api/v1/diaries/{diaryId}
auth: Y
controller: DiaryController.kt
handler: delete
status: mined
---

# DELETE /api/v1/diaries/{diaryId} — 일기 삭제

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `diaryId: Long`

## 2. 응답 (Response)
- 성공: `204 No Content`
- 실패: 401, 403/404

## 3. 비즈니스 로직 (요약)
1. `diaryFacade.delete(diaryId, userId)` → 작성자 검증 후 삭제.

## 4. 데이터 의존
- DB write: diaries (cascade: comments, likes)

## 5. 예외 케이스
- 작성자 아님 → 403/404
- 이미 삭제됨 → 404 또는 idempotent

## 6. 암묵적 로직 (Implicit)
- soft-delete vs hard-delete 미확인.
- 연관 데이터(댓글, 좋아요, 채팅방) cascade 정책.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] cascade 정책 명시
- [ ] soft-delete 여부

## 9. KEEP/DROP/FIX 분류

**판정: FIX** (KEEP + 변경 사항 박제)

### KEEP 사유
- 일기 삭제는 사용자 자기 통제권 핵심. 다른 도메인 (comment / diary_like / diarychat / sentence-feedback / platform 랭킹) 으로 cascade 영향 큼 → Saga 패턴 박제 필요.

### FIX 항목

상세 박제: [`docs/decisions/diary/diary-domain-policy.md`](../../decisions/diary/diary-domain-policy.md).

1. **ID 타입 UUID** — `diaryId: Long` → `UUID`.
2. **hard-delete 채택** — soft-delete 미채택 (comment 정합). UX / GDPR / 운영 단순화.
3. **권한: 작성자 only** — 본인 외 삭제 불가. moderation / 신고 시스템은 후속.
4. **404 통일 (IDOR 보호)** — 작성자 아님 / 일기 없음 / 비공개 + 비작성자 모두 404.
5. **응답: `204 No Content`** — body 없음.
6. **비멱등 (이미 삭제 → 404)** — comment 정합.
7. **cascade 정책 — DiaryDeleted Saga 패턴** — diary-service 자체 트랜잭션은 `diaries` row 삭제 + Outbox `DiaryDeleted` insert 만. 연관 데이터 (comments / diary_likes / diarychat / sentence-feedback / platform 랭킹) 는 `DiaryDeleted` Kafka 이벤트 구독자가 비동기 처리. **2PC / cross-service 트랜잭션 X** ([CLAUDE.md](../../../CLAUDE.md) 의 분산 트랜잭션 금지). 부분 실패 시 보상 트랜잭션은 후속 (현재 best-effort).
8. **DiaryDeleted contracts 미정의** — Kafka `DiaryDeleted` 이벤트 record 미작성 ([contracts-catalog](../../architecture/contracts-catalog.md) §도메인 이벤트(diary) "📝 미작성"). **본 PR 시점 미박제** (사용자 정책 "선행 필요만 기록"). 별도 contracts PR 로 박제 후 본 PRD 의 구현 PR 진행. 이벤트 필드 (`eventId / occurredAt / diaryId / authorId`) 와 구독자 (platform / diary-service 자체 / chat-service / learning-service) 명세는 후속 contracts PR.
9. **연관 데이터 cascade 명시** — comments (hard-delete cascade), diary_likes (hard-delete), diarychat (참여자 수신 종료 + 메시지 보존 / 또는 일괄 삭제 — diarychat 평가 D-a-4 시점 정합), sentence-feedback (정리 — D-a-5 시점 정합), platform (ActivityHappened 음수 보상 또는 차감 이벤트). 정확한 핸들러 동작은 각 sub-도메인 평가 시점에 박제.

### 영향 범위 (구현 PR 에서)
- diary-service: `DeleteDiaryService` + Outbox DiaryDeleted insert + `DiaryRepository.deleteById` (hard-delete) + 작성자 검증.
- contracts: **별도 PR 에서 DiaryDeleted record 박제 후** 본 PRD 구현 진행.
- 다른 서비스: DiaryDeleted Kafka 구독자 (platform 랭킹 / chat-service / learning-service) 핸들러 — 각 도메인 평가 시점 정합.
