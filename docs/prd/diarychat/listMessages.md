---
api_id: diarychat.listMessages
http_method: GET
path: /api/v1/diary-chatrooms/{roomId}/messages
auth: Y
controller: DiaryChatMessageController.kt
handler: listMessages
status: mined
---

# GET /api/v1/diary-chatrooms/{roomId}/messages — 일기 채팅방 메시지 히스토리

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `roomId: Long`
- Query: `before?: Long` (메시지 ID), `size?: Int (default 30)`

## 2. 응답 (Response)
- 성공: `200 OK` + `DiaryChatDto.MessageHistoryResponse { items, hasMore, oldestMessageId }`

## 3. 비즈니스 로직 (요약)
1. `facade.history(roomId, userId, before, size)` → `before` ID 이전 메시지 페이지 조회 (역방향 페이징).

## 4. 데이터 의존
- DB read: diary_chat_messages (room_id 필터, message_id 기준 정렬)

## 5. 예외 케이스
- 권한 없음 → 403/404

## 6. 암묵적 로직 (Implicit)
- ID 기반 cursor 페이징(`before`) — 시간 기반이 아니라 message_id 단조 증가 가정.
- size 기본 30, max 미명시.

## 7. 호출자 (Clients)
- 모바일/웹 (스크롤 업)

## 8. TODO / Open Questions
- [ ] size 상한
- [ ] 삭제된 메시지 표시 정책

## 9. KEEP/DROP/FIX 분류

**KEEP+FIX** — [`decisions/diary/diarychat-domain-policy.md`](../../decisions/diary/diarychat-domain-policy.md) §14 박제 적용.

| 항목 | 결정 | 박제 § |
|---|---|---|
| Path `roomId` 타입 | `Long` → **UUID** | §1 |
| Query `before` 타입 | `Long` (message ID) → **UUID** + opaque base64 cursor `(created_at, message_id)` | §14 |
| `size` default | 30 (mined 유지) | §14 |
| `size` max | **100** (mined 미명시 부채 해소) | §14 |
| 정렬 | 작성일 내림차순 (최근부터 역방향, 스크롤 업 UX) | §14 |
| 권한 | 참여자 only, 비참여자 → 404 (IDOR) | §4 |
| 비공개 일기 + 비작성자 | 404 | §3, §4 |
| 삭제된 방 | 404 | §16 |
| 응답 schema | `{ items: List<ChatMessageResponse>, nextCursor: String?, hasMore: Boolean, oldestMessageId: UUID? }` ([comment §6](../../decisions/diary/comment-domain-policy.md#6-페이징-list) 정합) | §13, §14 |
| 메시지 schema | `ChatMessageResponse` 8 필드 (audioUrl 포함, lazy 미생성 시 null) | §13 |
| 삭제 메시지 | **미지원** (Non-Goals — soft-delete X / hard-delete X 본 PR 시점) | §14 |
| audioUrl 채움 시점 | lazy GET 후에만 NOT NULL. 클라가 필요 시 [`getMessageAudio.md`](getMessageAudio.md) 호출 | §11, §14 |

근거: comment / diary 의 cursor 페이징 패턴 정합 (`(created_at, id)` base64). mined 의 message_id Long 단조 증가 가정 → UUID + created_at 보조 정렬.

후속 (Open Questions §8 해소):
- size 상한 = 100 박제.
- 삭제된 메시지 = 미지원 (Non-Goals).

