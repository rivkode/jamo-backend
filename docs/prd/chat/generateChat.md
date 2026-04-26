---
api_id: chat.generateChat
http_method: POST
path: /api/v1/chat/generate
auth: Y
controller: ChatApiController.kt
handler: generateChat
status: mined
---

# POST /api/v1/chat/generate — AI 채팅 응답 생성

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: `ChatDto.GenerateRequest` (`@Valid`)

## 2. 응답 (Response)
- 성공: `201 Created` + `ChatDto.RegisterResponse(chatInfo)` (생성된 AI 응답)

## 3. 비즈니스 로직 (요약)
1. `chatFacade.generateChat(command, userId)` → AI 모델 호출하여 응답 생성 + 저장.

## 4. 데이터 의존
- DB write: chat 메시지 (사용자 입력 + AI 응답)
- 외부 API: AI 모델 (OpenAI / 자체 LLM 등)
- Redis: 컨텍스트/캐시 가능성

## 5. 예외 케이스
- 외부 AI API 실패 → 500/502
- validation → 400

## 6. 암묵적 로직 (Implicit)
- registerChat과 응답 DTO가 동일(`RegisterResponse`)이지만, 의미는 "사용자 입력 + AI 응답 묶음" 가능.
- 외부 호출 타임아웃·재시도 정책 확인 필요.

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
- 모바일/웹 채팅 화면

## 8. TODO / Open Questions
- [ ] AI 응답 스트리밍 여부 (현재는 아님으로 보임)
- [ ] 외부 모델 호출 retry/timeout
- [ ] Rate limit / 사용자별 quota

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
