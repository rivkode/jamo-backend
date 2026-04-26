---
api_id: chat.greeting
http_method: POST
path: /api/v1/chat/greeting
auth: Y
controller: ChatApiController.kt
handler: greeting
status: mined
---

# POST /api/v1/chat/greeting — AI 인사말 채팅 생성

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: `ChatDto.GenerateRequest` (`@Valid`) — `generateChat`과 동일 DTO 재사용

## 2. 응답 (Response)
- 성공: `201 Created` + `ChatDto.RegisterResponse(chatInfo)`

## 3. 비즈니스 로직 (요약)
1. `chatFacade.greetingChat(userId, command)` → AI 인사말 생성/저장.

## 4. 데이터 의존
- DB write: chat (인사말 메시지)
- 외부 API: AI 모델

## 5. 예외 케이스
- 외부 모델 실패 → 5xx

## 6. 암묵적 로직 (Implicit)
- 채팅방 시작 시점에 호출되는 시드 메시지 추정.
- `GenerateRequest`를 재사용하지만 의미는 다름 (Open Question).

## 7. 호출자 (Clients)
- 모바일/웹 (채팅방 진입)

## 8. TODO / Open Questions
- [ ] greeting과 generate의 DTO/로직 차이 명확화
- [ ] 인사말이 매번 생성되는지 / 캐시되는지

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
