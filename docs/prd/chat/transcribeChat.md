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

## 7. 호출자 (Clients)
- 모바일 (음성 녹음 후 업로드)

## 8. TODO / Open Questions
- [ ] 최대 파일 크기 / 포맷 제한
- [ ] STT 비용 / quota

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: 디버그 println **`@DROP`**
