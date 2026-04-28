---
api_id: diarychat.listParticipants
http_method: GET
path: /api/v1/diary-chatrooms/{roomId}/participants
auth: Y
controller: DiaryChatRoomController.kt
handler: listParticipants
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

## 9. KEEP/DROP/FIX 분류

**KEEP+FIX** — [`decisions/diary/diarychat-domain-policy.md`](../../decisions/diary/diarychat-domain-policy.md) 박제 적용.

| 항목 | 결정 | 박제 § |
|---|---|---|
| Path `roomId` 타입 | `Long` → **UUID** | §1 |
| 권한 가드 | **참여자 only**, 비참여자 → 404 (IDOR) | §4 |
| 비공개 일기 + 비작성자 | 404 | §3, §4 |
| 삭제된 방 | 404 | §16 |
| 응답 schema | `ParticipantItem { userId: UUID, displayName: String, joinedAt: Instant }` 3 필드 | §13 |
| displayName 조립 | `UserSummaryService.BatchGetUserSummaries` (PR #35, max 200) 일괄 조회 | §13 |

근거: §6 의 "권한 검사 누락 가능성 (userId 가 facade 에 안 들어감)" 부채 — Application Service 가 userId 받아 참여자 검증 후 응답 조립. `BatchGetUserSummaries` 로 N+1 회피.

후속 (Open Questions §8 해소): 비참여자 접근 차단 → 404 통일 박제 완료.

