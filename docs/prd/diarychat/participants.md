---
api_id: diarychat.participants
http_method: GET
path: /api/v1/diary-chatrooms/{roomId}/participants
auth: Y
controller: DiaryChatRoomController.kt
handler: participants
status: mined
---

# GET /api/v1/diary-chatrooms/{roomId}/participants — 채팅방 참여자 목록

## 1. 요청 (Request)
- Header: `@LoginUser` (시그니처에 있으나 Facade 호출에 미사용)
- Path: `roomId: Long`

## 2. 응답 (Response)
- 성공: `200 OK` + `DiaryChatDto.ParticipantListResponse(items)`

## 3. 비즈니스 로직 (요약)
1. `facade.getParticipants(roomId)` → 참여자 목록 → DTO 매핑.

## 4. 데이터 의존
- DB read: diary_chat_participants

## 5. 예외 케이스
- 방 없음 → 404

## 6. 암묵적 로직 (Implicit)
- **권한 검사 누락 가능성** (userId가 facade에 안 들어감, get과 동일 패턴).

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] 비참여자 접근 차단

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: 권한 검사 추가 → `@FIX`
