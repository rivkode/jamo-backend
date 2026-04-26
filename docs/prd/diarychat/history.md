---
api_id: diarychat.history
http_method: GET
path: /api/v1/diary-chatrooms/{roomId}/messages
auth: Y
controller: DiaryChatMessageController.kt
handler: history
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

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
