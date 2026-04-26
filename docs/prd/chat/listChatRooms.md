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
- [ ] 페이징 도입 필요 여부
- [ ] 정렬 기준 (최근 메시지/생성일)

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: 페이징 도입 → `@FIX`
