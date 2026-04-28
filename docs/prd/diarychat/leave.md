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

## 9. KEEP/DROP/FIX 분류

**DROP** — [`decisions/diary/diarychat-domain-policy.md`](../../decisions/diary/diarychat-domain-policy.md) §7 박제 적용.

근거 — 사용자 명시:
> 나간다는 요구사항은 없어.

| 항목 | 결정 | 박제 § |
|---|---|---|
| 분류 | **DROP** | §7 |
| HTTP endpoint | 미구현 (`POST /leave` 미배포) | §7 |
| 파일 처리 | 본 §9 에 DROP 표기 + **파일 보존** (history 추적) | §7 |
| 방 종료 행위 | 사용자가 명시적으로 leave 하지 않음 — `DiaryDeleted` Saga cascade 만이 방을 종료 (`chatrooms.deleted_at` soft-delete) | §7, §16 |
| 방장 / owner 모델 | 도입하지 않음 (별 개념 미도입, [diarychat §2](../../decisions/diary/diarychat-domain-policy.md#2-방-생성--누구나-일기당-단일-방)) | §2 |

후속 (Open Questions §8 해소):
- 방장 퇴장 처리: 방장 / owner 개념 자체를 도입하지 않으므로 N/A. 일기 작성자가 aiToggle 권한자 — 작성자 leave 자체가 N/A (leave 없음).

