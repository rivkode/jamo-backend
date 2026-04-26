---
api_id: chat.hello
http_method: GET
path: /api/v1/chat/hello
auth: N
controller: ChatApiController.kt
handler: hello
status: mined
---

# GET /api/v1/chat/hello — 헬스/그리팅 메시지

## 1. 요청 (Request)
- 없음

## 2. 응답 (Response)
- 성공: `200 OK` + `ChatDto.HelloResponse(message: String)`

## 3. 비즈니스 로직 (요약)
1. `chatFacade.hello()` 호출 → 고정/단순 인사말 반환.

## 4. 데이터 의존
- 없음 (또는 trivial)

## 5. 예외 케이스
- 없음

## 6. 암묵적 로직 (Implicit)
- 헬스체크 또는 데모용. 운영용 healthcheck는 별도일 가능성.

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
- 개발/테스트용 추정

## 8. TODO / Open Questions
- [ ] 실제 운영에서 호출 여부

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: `@DROP` (운영 미사용 시)
