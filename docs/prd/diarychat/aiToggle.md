---
api_id: diarychat.aiToggle
http_method: POST
path: /api/v1/diary-chatrooms/{roomId}/ai-toggle
auth: Y
controller: DiaryChatRoomController.kt
handler: aiToggle
status: mined
---

# POST /api/v1/diary-chatrooms/{roomId}/ai-toggle — AI 어시스턴트 on/off

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `roomId: Long`
- Body: `DiaryChatDto.AiToggleRequest { enabled: Boolean }` (`@Valid`)

## 2. 응답 (Response)
- 성공: `200 OK` + `DiaryChatDto.ChatRoomResponse`

## 3. 비즈니스 로직 (요약)
1. `facade.setAiAssistant(roomId, userId, request.enabled)` → 방 설정 변경.

## 4. 데이터 의존
- DB write: diary_chat_rooms.ai_assistant_enabled

## 5. 예외 케이스
- 권한 없음 → 403

## 6. 암묵적 로직 (Implicit)
- 명시적 boolean으로 멱등성 확보.
- 누가 토글할 권한이 있는지(방장만 vs 모든 참여자) 정책 확인 필요.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 토글 권한 정책

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
