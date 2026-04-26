---
api_id: chat.registerAnswer
http_method: POST
path: /api/v1/answers
auth: Y
controller: AnswerApiController.kt
handler: registerAnswer
status: mined
---

# POST /api/v1/answers — 답변 생성 (AI)

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: `AnswerDto.RegisterRequest` (⚠️ `@Valid` 누락)

## 2. 응답 (Response)
- 성공: `201 Created` + `AnswerDto.RegisterResponse(answerInfo)`

## 3. 비즈니스 로직 (요약)
1. `answerFacade.generateAnswer(userId, registerAnswer)` → AI로 답변 생성·저장.

## 4. 데이터 의존
- DB write: answers
- 외부 API: AI 모델

## 5. 예외 케이스
- 외부 모델 실패 → 5xx

## 6. 암묵적 로직 (Implicit)
- 핸들러명은 `registerAnswer`인데 Facade 메서드는 `generateAnswer` — 사용자가 답변 입력이 아니라 **AI 생성**임을 시사.
- `@Valid` 미적용.

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
- [ ] 사용자 직접 입력 vs AI 생성 구분
- [ ] questionId가 Body에 포함되는가

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: `@Valid` 추가 → `@FIX`
