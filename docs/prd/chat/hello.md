---
api_id: chat.hello
http_method: GET
path: /api/v1/chat/hello
auth: N
controller: ChatApiController.kt
handler: hello
status: mined
---

# GET /api/v1/chat/hello — 헬스/그리팅 메시지

## 1. 요청 (Request)
- 없음

## 2. 응답 (Response)
- 성공: `200 OK` + `ChatDto.HelloResponse(message: String)`

## 3. 비즈니스 로직 (요약)
1. `chatFacade.hello()` 호출 → 고정/단순 인사말 반환.

## 4. 데이터 의존
- 없음 (또는 trivial)

## 5. 예외 케이스
- 없음

## 6. 암묵적 로직 (Implicit)
- 헬스체크 또는 데모용. 운영용 healthcheck는 별도일 가능성.

## 7. 호출자 (Clients)
- 개발/테스트용 추정

## 8. TODO / Open Questions
- [ ] 실제 운영에서 호출 여부

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: `@DROP` (운영 미사용 시)
