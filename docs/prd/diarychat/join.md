---
api_id: diarychat.join
http_method: POST
path: /api/v1/diary-chatrooms/{roomId}/join
auth: Y
controller: DiaryChatRoomController.kt
handler: join
status: mined
---

# POST /api/v1/diary-chatrooms/{roomId}/join — 채팅방 입장

## 1. 요청 (Request)
- Header: `@LoginUser`
- Path: `roomId: Long`

## 2. 응답 (Response)
- 성공: `200 OK` + `DiaryChatDto.ChatRoomResponse`

## 3. 비즈니스 로직 (요약)
1. `facade.join(roomId, userId)` → participants에 추가 (이미 있으면 idempotent 추정).

## 4. 데이터 의존
- DB write: diary_chat_participants

## 5. 예외 케이스
- 방 없음 → 404
- 입장 정원 초과 등 정책 위반 → 403/409 (정책 확인 필요)

## 6. 암묵적 로직 (Implicit)
- 멱등성 가정 — 이미 참여 중인 사용자가 재호출해도 정상 응답.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 비공개 일기 채팅방의 입장 권한
- [ ] 차단 사용자 처리

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
