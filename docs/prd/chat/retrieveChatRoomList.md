---
api_id: chat.retrieveChatRoomList
http_method: GET
path: /api/v1/chatrooms
auth: Y
controller: ChatRoomApiController.kt
handler: retrieveChatRoomList
status: mined
---

# GET /api/v1/chatrooms — 내 채팅방 목록

## 1. 요청 (Request)
- Header: `@LoginUser`
- Query/Page: 없음 (현재 구현 — 페이징 없음)

## 2. 응답 (Response)
- 성공: `200 OK` + `ChatRoomDto.Response(chatRoomListInfo)`

## 3. 비즈니스 로직 (요약)
1. `chatFacade.retrieveChatRoom(userId)` → 사용자 소유 채팅방 전체 반환.

## 4. 데이터 의존
- DB read: chat_rooms

## 5. 예외 케이스
- 인증 실패 → 401

## 6. 암묵적 로직 (Implicit)
- **페이징 없음** — 채팅방 수가 많아지면 응답 비대화.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 페이징 도입 필요 여부
- [ ] 정렬 기준 (최근 메시지/생성일)

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: 페이징 도입 → `@FIX`
