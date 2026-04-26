---
api_id: chat.speechAudio
http_method: POST
path: /api/v1/chat/speech
auth: Y
controller: ChatApiController.kt
handler: speechAudio
status: mined
---

# POST /api/v1/chat/speech — 텍스트 → 음성(TTS)

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: `ChatDto.SpeechRequest` (`@Valid`)

## 2. 응답 (Response)
- 성공: `200 OK` + `ChatDto.SpeechResponse(speechInfo)` (오디오 URL or base64 추정)

## 3. 비즈니스 로직 (요약)
1. `chatFacade.speechAudio(command, userId)` → 외부 TTS 호출, 결과 저장/URL 반환.

## 4. 데이터 의존
- 외부 API: TTS (OpenAI / Google TTS 등)
- 파일 시스템 / S3: 오디오 저장

## 5. 예외 케이스
- TTS 실패 → 5xx

## 6. 암묵적 로직 (Implicit)
- 응답이 audio URL인지 binary stream인지 DTO 확인 필요.

## 7. 호출자 (Clients)
- 모바일/웹 (AI 응답 음성 재생)

## 8. TODO / Open Questions
- [ ] 오디오 보존 기간
- [ ] 사용자 음성 선택(voice/lang) 파라미터

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
