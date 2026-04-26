---
api_id: diarychat.get
http_method: GET
path: /api/v1/diary-chatrooms/{roomId}
auth: Y
controller: DiaryChatRoomController.kt
handler: get
status: mined
---

# GET /api/v1/diary-chatrooms/{roomId} — 일기 채팅방 단건 조회

## 1. 요청 (Request)
- Header: `@LoginUser` (시그니처에 있으나 Facade 호출에 사용되지 않음)
- Path: `roomId: Long`

## 2. 응답 (Response)
- 성공: `200 OK` + `DiaryChatDto.ChatRoomResponse`

## 3. 비즈니스 로직 (요약)
1. `facade.get(roomId)` → 방 조회.

## 4. 데이터 의존
- DB read: diary_chat_rooms

## 5. 예외 케이스
- 없음 → 404

## 6. 암묵적 로직 (Implicit)
- **`userId`가 시그니처엔 있는데 Facade 호출에 안 넘김** — 권한 검사 미적용 또는 누구나 조회 가능. (`@FIX` 후보)

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 비참여자 접근 차단 여부

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: 권한 검사 추가 → `@FIX`
