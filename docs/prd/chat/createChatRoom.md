---
api_id: chat.createChatRoom
http_method: POST
path: /api/v1/chatrooms
auth: Y
controller: ChatRoomApiController.kt
handler: createChatRoom
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

## 6.1 AI 호출 위임 (ADR-0003)
- chat-service 는 직접 LLM/STT/TTS 를 호출하지 않고 **ai-service (Python, gRPC)** 에 위임한다.
- 흐름: chat-service → `AiService.{complete|speechToText|textToSpeech}` (Deadline + Retry 1회 + Circuit Breaker) → ai-service → OpenAI / Whisper / vLLM / 자체 모델.
- chat-service 책임: 프롬프트 템플릿 / 사용자 컨텍스트 / 사용량 / rate limit / fallback 메시지.
- ai-service 책임: 순수 AI 추론 (LLM + STT + TTS). 무상태.

**추후 chat-service / ai-service 구현 시 고려사항**:
- 응답 streaming (현재는 unary 시작, server-streaming 도입 시 본 PRD 응답 형식 영향)
- 사용량 / 비용 추적 단위 (사용자별 / API별 / 토큰별)
- ai-service 장애 시 fallback UX (정형 메시지 vs 5xx)
- 프롬프트 템플릿 버전 관리 (chat 스키마에 영속)
- AI 응답의 PII / 금칙어 sanitization 위치 (chat-service vs ai-service)
- 음성(STT/TTS) 데이터 처리: 바이너리 크기 제한, 파일 보관 정책

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 동일 사용자 중복 채팅방 생성 정책
- [ ] 채팅방 메타데이터(이름/타입 등)

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: `@Valid` 추가 → `@FIX`
