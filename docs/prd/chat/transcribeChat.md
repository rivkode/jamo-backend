---
api_id: chat.transcribeChat
http_method: POST
path: /api/v1/chat/transcribe
auth: Y
controller: ChatApiController.kt
handler: transcribeChat
status: mined
---

# POST /api/v1/chat/transcribe — 오디오 파일 → 텍스트 전사 (multipart)

## 1. 요청 (Request)
- Header: `@LoginUser`, multipart/form-data
- Query: `chatRoomId: Long` (`@RequestParam`)
- Part: `audio: MultipartFile`

## 2. 응답 (Response)
- 성공: `200 OK` + `ChatDto.TranscribeResponse(transcribeInfo)` (JSON)

## 3. 비즈니스 로직 (요약)
1. CORS/디버그 헤더(`Origin/Referer/UA/Host`) 콘솔 출력 (현 구현)
2. `chatFacade.transcribeAudio(userId, chatRoomId, audioFile)` 호출
3. 응답 DTO 래핑

## 4. 데이터 의존
- DB write: 전사 결과 저장 가능성
- 외부 API: STT (OpenAI Whisper / Google STT 등)
- 파일 시스템 / S3: 오디오 임시 저장 가능성

## 5. 예외 케이스
- 파일 누락/형식 오류 → 400
- STT 실패 → 5xx

## 6. 암묵적 로직 (Implicit)
- **`println("=== CORS DEBUG INFO ===" ...)` 디버그 출력이 운영 코드에 남음** — `@DROP` 후보.
- multipart consumes/produces가 명시됨.

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
- 모바일 (음성 녹음 후 업로드)

## 8. TODO / Open Questions
- [ ] 최대 파일 크기 / 포맷 제한
- [ ] STT 비용 / quota

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: 디버그 println **`@DROP`**
