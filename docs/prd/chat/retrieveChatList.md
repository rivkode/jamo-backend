---
api_id: chat.retrieveChatList
http_method: GET
path: /api/v1/chatrooms/{chatRoomId}/chat
auth: Y
controller: ChatRoomApiController.kt
handler: retrieveChatList
status: mined
---

# GET /api/v1/chatrooms/{chatRoomId}/chat — 특정 채팅방의 메시지 목록

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `chatRoomId`
- Query/Page: 없음 (페이징 없음)

## 2. 응답 (Response)
- 성공: `200 OK` + `ChatDto.ChatListResponse(chatListInfo)`

## 3. 비즈니스 로직 (요약)
1. `chatFacade.retrieveChat(userId, chatRoomId)` → 메시지 전체 반환.

## 4. 데이터 의존
- DB read: chats (chat_room_id로 필터)

## 5. 예외 케이스
- 인증 실패 → 401
- 권한 없는 채팅방 접근 → 403/404 (Facade 내부 가드)

## 6. 암묵적 로직 (Implicit)
- 페이징 없음 — 장기간 사용 시 응답 비대화 (`@FIX` 후보).
- 소유자/참여자 검증 위치 확인 필요.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 페이징/cursor 도입
- [ ] 권한 검증 위치(Facade vs Service)

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
