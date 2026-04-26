---
api_id: diarychat.create
http_method: POST
path: /api/v1/diary-chatrooms
auth: Y
controller: DiaryChatRoomController.kt
handler: create
status: mined
---

# POST /api/v1/diary-chatrooms — 일기 채팅방 생성 또는 기존 조회 (createOrGet)

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: `DiaryChatDto.CreateChatRoomRequest { diaryId, aiAssistantEnabled }` (`@Valid`)

## 2. 응답 (Response)
- 성공: `200 OK` + `DiaryChatDto.ChatRoomResponse`

## 3. 비즈니스 로직 (요약)
1. `CreateChatRoomCommand(diaryId, aiAssistantEnabled, requesterUserId=userId)` 생성
2. `facade.createOrGet(...)` → 기존 방 있으면 반환, 없으면 신규 생성.

## 4. 데이터 의존
- DB read/write: diary_chat_rooms

## 5. 예외 케이스
- 일기 없음 → 404

## 6. 암묵적 로직 (Implicit)
- **`createOrGet` 멱등성** — 중복 생성 안전.
- 응답 status는 `200 OK` (생성·조회 통합이라 201 아님).

## 7. 호출자 (Clients)
- 모바일/웹 (일기 상세에서 채팅 진입 시)

## 8. TODO / Open Questions
- [ ] aiAssistantEnabled의 사용자별 디폴트
- [ ] 다중 사용자가 같은 일기에 채팅방 생성 시 단일 방 vs 별도 방

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
