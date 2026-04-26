---
api_id: diarychat.leave
http_method: POST
path: /api/v1/diary-chatrooms/{roomId}/leave
auth: Y
controller: DiaryChatRoomController.kt
handler: leave
status: mined
---

# POST /api/v1/diary-chatrooms/{roomId}/leave — 채팅방 퇴장

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `roomId: Long`

## 2. 응답 (Response)
- 성공: `204 No Content`

## 3. 비즈니스 로직 (요약)
1. `facade.leave(roomId, userId)` → participants에서 제거.

## 4. 데이터 의존
- DB write: diary_chat_participants

## 5. 예외 케이스
- 비참여자가 호출 → 멱등 204 또는 404 (정책 확인)

## 6. 암묵적 로직 (Implicit)
- 마지막 참여자가 나가면 방이 닫히는지 확인 필요.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 방장(owner) 퇴장 시 처리

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
