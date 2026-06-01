# Decision: diarychat 도메인 정책 v2 — API_SPEC 부록 E.2 정합 (옛 정책 supersede)

- **상태**: Accepted
- **결정일**: 2026-06-01
- **결정자**: jonghun
- **관련 PR**: feature/diary-chatroom-room (S2-a), 후속 messaging (S2-b)
- **SoT**: [`API_SPEC.md`](../../../API_SPEC.md) 부록 E.2 (프론트 실제 동작 계약)
- **supersedes**: [`diarychat-domain-policy.md`](diarychat-domain-policy.md) (2026-04-28) — 아래 §"충돌·폐기" 항목

## 배경

`diarychat-domain-policy.md` 는 mined PRD 평가 시점(2026-04-28) 박제이나, 이후 프론트엔드가 **API_SPEC 부록 E.2** 계약으로 구현·동작 중이다. 두 명세가 핵심에서 충돌하며, **프론트가 이미 동작 중인 부록 E.2 가 진실(SoT)** 이다. 본 문서가 충돌 항목을 supersede 한다. (ddd-architect 검증 반영)

## 충돌·폐기 (옛 정책 → 부록 E.2)

| 항목 | 옛 정책 (폐기) | **부록 E.2 (채택)** |
|---|---|---|
| roomId / messageId | UUID (§1) | **int64 숫자** — 롱폴 `before`/`after` 숫자 커서 의존 |
| STT 위치 | 서버가 send 안에서 호출 (§9) | **클라이언트** 책임 — 서버는 텍스트만 수신 |
| AI 응답 | send/join 동기 반환 (§10) | 서버 생성 → **롱폴 `poll` 로 전달** (LLM 생성은 S4) |
| leave | DROP (§7) | **구현** (E2.5, 204) |
| send 응답 | [유저+AI] 둘 다 | 유저 메시지만 (`source:user`) |
| 음성 재생 | lazy GET `/messages/{id}/audio` (§11) | `audioUrl` 필드 + 정적 `/audio/{name}` 서빙 (E.5 별도 구현) |

옛 정책의 워크플로(§6 join 트리거 / §10 동기 AI)는 폐기. ID 일관(§1 UUID)도 diarychat 한정 폐기 — diaryId/userId 는 UUID 유지.

## 결정 (v2)

### 1. ID 생성 — MySQL auto-increment (late identity)

- roomId / messageId = `BIGINT AUTO_INCREMENT`. 단일 MySQL 인스턴스라 **ID 순서 = INSERT 순서 = 시간 순서** 보장 → 롱폴 숫자 커서(before/after) 정합.
- 도메인은 `RoomId(long)` / `MessageId(long)` record VO 로 래핑 (원시 long 비노출). ID 는 **영속 후 확정**(late identity) — `save()` 가 생성 키를 채운 Aggregate 를 반환. transient(영속 전) 인스턴스는 컬렉션/이벤트 수집에 사용하지 않는다.
- diaryId / userId 는 외래 UUID 유지 (identity / diary 정합, ADR-0005 — FK 없음 INDEX 만).

### 2. 권한 — 404 IDOR 통일, 단 ai-toggle 만 403

| 시나리오 | HTTP | 근거 |
|---|---|---|
| 방 없음 / 비공개 일기 비작성자 / 비참여자 호출 | **404** | 자원 은닉 (IDOR) — diary/comment 정합 |
| 참여자지만 비호스트의 ai-toggle | **403** | 방은 이미 노출됨(참여자) — IDOR 아님, 권한 부족. E2.6 명세 명시 |

분기 기준: **자원 가시성**. 자원을 정당하게 알 수 없으면 404, 알 수 있으나 행위 권한이 없으면 403.

### 3. host — 파생 (participant 미저장)

- `isHost = (room.hostUserId == participant.userId)` 파생. `hostUserId` = 일기 작성자(불변, `diary.authorId`).
- ChatParticipant 는 isHost 컬럼 미보유 → host leave 후 rejoin 시 isHost 복원 로직 불요.

### 4. participantCount — 파생 (count 쿼리)

- `countByRoomId` 단건 쿼리. 단일 방 조회에서만 필요 → denormalized counter 불요. `unique(room_id,user_id)` 가 멱등 join 의 count 정확성 자동 보장.

### 5. createOrGet — 일기당 1방 멱등

- `findByDiaryId` 선조회 → 있으면 200 반환, 없으면 생성 후 201. `unique(diary_id)` 제약 + 동시 생성 race 시 UNIQUE 위반 catch → re-find (DiaryLike/Comment race fallback 정합).
- 권한: **누구나** createOrGet (비공개 일기 비작성자는 참여 자격 없음 → 404).
- hostUserId = `diary.authorId`. aiAssistantEnabled 초기값 = 요청 default true.

### 5-b. get / participants 접근 — 일기 가시성 기반 (참여 여부 무관)

- GET room / participants 는 **참여 여부가 아니라 일기 가시성**으로 인가 (createOrGet 이 이미 방을 노출하므로 비참여자도 조회 가능 — 공개 일기 한정). 비공개 일기 비작성자는 가드에서 404.
- participants 응답은 `userId` + `displayName`(+avatarUrl null) 만 — email/실명 등 직접 PII 미노출. 공개 일기 채팅방 참여자 명단이 비참여자에게 보이는 것은 의도된 결정 (security-reviewer 확인). 향후 차단/팔로우 등 소셜 그래프 도입 시 참여자/host 한정으로 재검토.

### 6. leave — 멱등 delete, 204

- `deleteByRoomIdAndUserId` 멱등 (0 row 도 204). 방 생애주기 무관 — 참여자 0 이어도 room active. `room.deletedAt` 은 **오직 DiaryDeleted Saga 만** 채운다(후속).

### 7. 삭제 — soft-delete (DiaryDeleted Saga cascade, 후속)

- room `deleted_at` soft-delete, 메시지/참여자 보존. 삭제된 방 모든 endpoint 404. Saga 구독은 후속 PR. 본 슬라이스는 `markDeleted` 도메인 메서드 자리만.

### 8-b. 메시지 / 롱폴 (S2-b 확정)

- **ChatMessage**: messageId BIGINT auto-increment, roomId, authorUserId(UUID, AI/SYSTEM 은 null), text(1..1000 cp), audioUrl(http/https, optional), source(USER/AI/SYSTEM), createdAt. 본 슬라이스는 USER 메시지(send)만 — STT 는 클라가 처리해 text 로 전송. AI/SYSTEM 은 S4.
- **send** (E2.9): {text, audioUrl?} → 201, source=user. 방 접근(가드) 통과 시 작성 (참여 강제 안 함 — get/participants 정합).
- **listMessages** (E2.7): before={messageId}&size(기본 30, 최대 100) → {items(최근 desc), hasMore, oldestMessageId}. before 없으면 최신부터.
- **poll** (E2.8): after={messageId}&wait(기본 25, **최대 30s** clamp — 게이트웨이 idle 보다 짧게). `DeferredResult` + 공유 스케줄 체커(약 0.7s 간격)로 servlet thread 비점유. 새 메시지(id>after) 또는 이벤트 발생 시 즉시 반환, 없으면 wait 후 빈 배열. nextAfter = items 의 max messageId (없으면 after).
- **events 소스** = `chat_room_events` append 테이블 (event_id BIGINT auto-inc, roomId, type, actorUserId, enabled?, createdAt). join/leave/ai-toggle 서비스가 append. poll 은 **시작 시점 baseline max event_id** 를 캡처해 그보다 큰 event 만 반환 (wait 윈도우 동안 발생분). <b>한계</b>: poll 응답과 다음 poll 시작 사이 gap 에 발생한 event 는 유실 가능 — 명세 요청에 event 커서가 없어 불가피. join/leave/ai-toggle 통지는 저위험(room/participants 재조회로 정합)이라 수용. type: PARTICIPANT_JOINED / PARTICIPANT_LEFT / AI_TOGGLE_CHANGED, enabled 는 AI_TOGGLE_CHANGED 만.
- **확장성 한계** (후속): 단일 인스턴스 in-memory 스케줄 체커 — 다중 인스턴스는 Redis pub/sub 또는 DB notify 필요. 메시지 author displayName 은 UserSummary BatchGet(N+1 회피).
- **후속 보안/운영 (security-reviewer 반영, 본 슬라이스 범위 밖)**: send 사용자·방 단위 rate limit(메시지 도배/AI 비용 abuse), 동시 poll 커넥션 상한(Tomcat async), audioUrl 자사 스토리지 도메인 화이트리스트(현재 서버 fetch 안 해 SSRF 무관), text/audioUrl 출력 인코딩은 클라 렌더 책임(서버 평문 저장 유지 — API_SPEC 계약), 일기/방 삭제 시 메시지·이벤트 애플리케이션 레벨 cascade(ADR-0005 FK 없음).

### 8. 롱폴 events (DiaryChatEvent) — §8-b 에서 확정 (chat_room_events append)

- poll 응답 `{ items, events, nextAfter }` 의 `events`(`ai_toggle_changed` 등)는 롱폴 전용 in-room 통지(Kafka 아님). events 소스(별도 append 테이블 vs 상태 diff) + 커서 정책은 **S2-b 착수 전 확정**. 본 S2-a 는 events 미구현(poll 없음).

## 슬라이스

- **S2-a**: room + participant 6 endpoint (createOrGet/get/participants/join/leave/ai-toggle). Aggregate: DiaryChatRoom, ChatParticipant.
- **S2-b**: messaging 3 endpoint (send/listMessages/poll + events). Aggregate: ChatMessage. 롱폴(DeferredResult).
- **S4**: AI 자동응답 (chat-service→ai-service LLM, ADR-0003).

## Non-Goals (본 v2 범위)

- WebSocket/SSE, 메시지 삭제/수정, 다중 방, 방장 위임/강퇴, moderation, AI stream.

## 참고

- [`API_SPEC.md`](../../../API_SPEC.md) §863-918 (부록 E.2)
- [`diarychat-domain-policy.md`](diarychat-domain-policy.md) (superseded 항목)
- [ADR-0005](../../adr/0005-no-jpa-associations.md) (FK 없음), [ADR-0003](../../adr/0003-ai-call-architecture.md) (AI 게이트웨이 — S4)
