---
api_id: chat.paraphrase
http_method: POST
path: /api/v1/chat/paraphrase
auth: N (⚠️ `@LoginUser` 미사용)
controller: ChatApiController.kt
handler: phrase
status: mined
---

# POST /api/v1/chat/paraphrase — 문장 패러프레이징

## 1. 요청 (Request)
- Body: `ChatDto.PhraseRequest` (`@Valid`)
- **인증: 컨트롤러 시그니처에 `@LoginUser` 없음** — 공개일 가능성. WebSecurity 설정 확인 필요.

## 2. 응답 (Response)
- 성공: `201 Created` + `ChatDto.RegisterResponse(chatInfo)`

## 3. 비즈니스 로직 (요약)
1. `chatFacade.phraseChat(command)` → AI로 문장 재구성.

## 4. 데이터 의존
- 외부 API: AI 모델
- DB write: 결과 저장 가능성

## 5. 예외 케이스
- 외부 호출 실패 → 5xx

## 6. 암묵적 로직 (Implicit)
- **다른 chat 엔드포인트와 달리 `@LoginUser` 없음** — 의도된 익명 허용인지 확인 필요(`@FIX` 후보).
- 응답 status가 201인데 read-only 변환 작업으로 보임 → 200이 더 적절할 수 있음.

## 7. 호출자 (Clients)
- 모바일/웹 (글쓰기 보조)

## 8. TODO / Open Questions
- [ ] 인증 누락이 의도된 것인지 확인
- [ ] 익명 허용 시 rate limit 필요

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: 인증 정책 명확화 → `@FIX`
