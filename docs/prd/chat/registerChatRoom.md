---
api_id: chat.registerChatRoom
http_method: POST
path: /api/v1/chatrooms
auth: Y
controller: ChatRoomApiController.kt
handler: registerChatRoom
status: mined
---

# POST /api/v1/chatrooms — 채팅방 생성

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: `ChatRoomDto.RegisterRequest` (⚠️ `@Valid` 미사용 — 검증 누락)

## 2. 응답 (Response)
- 성공: `201 Created` + `ChatRoomDto.RegisterResponse(chatRoomInfo)`

## 3. 비즈니스 로직 (요약)
1. `chatFacade.registerChatRoom(userId, command)` → 채팅방 생성.

## 4. 데이터 의존
- DB write: chat_rooms

## 5. 예외 케이스
- 인증 실패 → 401

## 6. 암묵적 로직 (Implicit)
- **Body에 `@Valid` 누락** — Bean Validation 어노테이션이 적용 안 됨. (`@FIX` 후보)

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 동일 사용자 중복 채팅방 생성 정책
- [ ] 채팅방 메타데이터(이름/타입 등)

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: `@Valid` 추가 → `@FIX`
