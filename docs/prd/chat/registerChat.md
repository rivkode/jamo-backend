---
api_id: chat.registerChat
http_method: POST
path: /api/v1/chat
auth: Y
controller: ChatApiController.kt
handler: registerChat
status: mined
---

# POST /api/v1/chat — 채팅 메시지 등록

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: `ChatDto.RegisterRequest` (`@Valid`)

## 2. 응답 (Response)
- 성공: `201 Created` + `ChatDto.RegisterResponse(chatInfo)`

## 3. 비즈니스 로직 (요약)
1. `request.toCommand()` 변환 → `chatFacade.registerChat(command, userId)` → 채팅 저장.

## 4. 데이터 의존
- DB write: chat 테이블

## 5. 예외 케이스
- validation 실패 → 400
- 인증 실패 → 401

## 6. 암묵적 로직 (Implicit)
- chatRoomId가 command 안에 포함되었는지 확인 필요 (Request DTO 점검).

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] `RegisterRequest` 필드 명세
- [ ] 멱등성 키 사용 여부

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
