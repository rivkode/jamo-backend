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

## 9. KEEP/DROP/FIX 분류

**KEEP+FIX** — [`decisions/diary/diarychat-domain-policy.md`](../../decisions/diary/diarychat-domain-policy.md) 박제 적용.

| 항목 | 결정 | 박제 §  |
|---|---|---|
| ID 타입 | `roomId / diaryId / userId` UUID | §1 |
| 권한 | **누구나** 생성 (작성자 제한 X — 사용자 명시) | §2, §19 |
| 멱등성 | `createOrGet`, 일기당 단일 방 (다중 방 거부 — Non-Goals) | §2 |
| 응답 코드 | 200 (생성·조회 통합 — 201 X) | §2 |
| `aiAssistantEnabled` 초기값 | 클라 요청 default = true | §2 |
| 비공개 일기 + 비작성자 | **404** (IDOR, [diary §3](../../decisions/diary/diary-domain-policy.md#2-공개비공개-정책--visibility-enum) 정합) | §3, §4 |
| 응답 schema | `ChatRoomResponse` 6 필드 (`roomId / diaryId / authorId / aiAssistantEnabled / participantCount / createdAt`) | §13 |

근거: "방장" 별 개념 미도입 — 일기 작성자 = aiToggle 권한자만으로 충분. `createOrGet` 멱등 + 1:1 단일 방.

후속 (Open Questions §8 부분 해소):
- aiAssistantEnabled 디폴트: 클라 요청 시 명시 (서버 default true). 사용자별 선호 저장은 후속.
- 다중 방: 거부 (단일 방). 후속 시 별 PR 재검토.

