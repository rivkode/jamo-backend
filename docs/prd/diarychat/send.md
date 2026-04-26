---
api_id: diarychat.send
http_method: POST
path: /api/v1/diary-chatrooms/{roomId}/messages
auth: Y
controller: DiaryChatMessageController.kt
handler: send
status: mined
---

# POST /api/v1/diary-chatrooms/{roomId}/messages — 일기 채팅 메시지 전송

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `roomId: Long`
- Body: `DiaryChatDto.SendMessageRequest { text, audioUrl? }` (`@Valid`)

## 2. 응답 (Response)
- 성공: `201 Created` + `DiaryChatDto.ChatMessageResponse`

## 3. 비즈니스 로직 (요약)
1. `SendMessageCommand(roomId, authorUserId=userId, text, audioUrl)` 생성
2. `facade.send(...)` → 메시지 저장 + (AI 토글 ON 시) AI 응답 트리거 가능성.

## 4. 데이터 의존
- DB write: diary_chat_messages
- 외부 API: AI 모델 (AI assistant ON 시 자동 응답)
- 폴링/푸시: 다른 참여자 수신 채널 (Polling endpoint와 결합)

## 5. 예외 케이스
- 권한 없음 → 403
- 방 닫힘/존재하지 않음 → 404

## 6. 암묵적 로직 (Implicit)
- text와 audioUrl 양립 가능 — 둘 다 동시 허용 시 동작 미확인.
- AI 응답 자동 트리거는 `aiAssistantEnabled` 플래그에 따라.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] text/audio 동시 처리 정책
- [ ] AI 응답 비동기/동기 흐름

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
