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

## 9. KEEP/DROP/FIX 분류

**KEEP+FIX** — [`decisions/diary/diarychat-domain-policy.md`](../../decisions/diary/diarychat-domain-policy.md) 박제 적용.

| 항목 | 결정 | 박제 § |
|---|---|---|
| Path `roomId` 타입 | `Long` → **UUID** | §1 |
| 권한 가드 | **참여자 only**, 비참여자 → 404 (IDOR, 403 미사용) | §4 |
| 비공개 일기 + 비작성자 | 404 (참여 자격 자체 없음) | §3, §4 |
| 삭제된 방 (`chatrooms.deleted_at NOT NULL`) | 404 | §16 |
| 응답 schema | `ChatRoomResponse` 6 필드 (`create.md` §9 정합) | §13 |

근거: §6 의 "userId 가 시그니처엔 있는데 Facade 호출에 안 넘김" 부채 — Application Service 가 userId 받아 권한 가드 (참여자 / 비공개 일기 가시성) 적용. 404 통일은 [comment §4](../../decisions/diary/comment-domain-policy.md#4-404-통일-idor-보호) / [diary §3](../../decisions/diary/diary-domain-policy.md#2-공개비공개-정책--visibility-enum) 일관.

