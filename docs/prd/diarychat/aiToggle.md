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

## 9. KEEP/DROP/FIX 분류

**KEEP+FIX** — [`decisions/diary/diarychat-domain-policy.md`](../../decisions/diary/diarychat-domain-policy.md) §5 박제 적용.

| 항목 | 결정 | 박제 § |
|---|---|---|
| Path `roomId` 타입 | `Long` → **UUID** | §1 |
| 토글 권한 | **일기 작성자 only** (비작성자 → **404** IDOR, 403 미사용) | §5, §4 |
| 비공개 일기 + 비작성자 | 404 (참여 자격 자체 없음) | §3, §4 |
| 멱등성 | 동일 `enabled` 재호출 안전 (boolean 명시 패턴, [comment §8](../../decisions/diary/comment-domain-policy.md#8-좋아요--명시적-boolean-멱등-설계-유지) 정합) | §5 |
| 응답 schema | `ChatRoomResponse` 6 필드 | §13 |
| OFF→ON 전환 시 자동 메시지 | **미생성** (웰컴 트리거는 `join` 매 호출에 흡수, [§6](../../decisions/diary/diarychat-domain-policy.md#6-ai-응답-트리거--join-매-호출)) | §6 |

근거 — 사용자 명시 + §6 박제: AI 응답 트리거는 `join` 매 호출 1곳으로 일원화. aiToggle 은 단순 설정 변경 endpoint.

권한 변경: §5 의 "권한 없음 → 403" 표기 → **404 통일** 로 정정 ([comment §4](../../decisions/diary/comment-domain-policy.md#4-404-통일-idor-보호) / [diary §3](../../decisions/diary/diary-domain-policy.md#2-공개비공개-정책--visibility-enum) 정합).

후속 (Open Questions §8 해소): 토글 권한 = 일기 작성자 only 박제 완료.

