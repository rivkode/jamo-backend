---
api_id: diary.create
http_method: POST
path: /api/v1/diaries
auth: Y
controller: DiaryController.kt
handler: create
status: mined
---

# POST /api/v1/diaries — 일기 작성

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: `DiaryDto.CreateRequest` (`@Valid`)

## 2. 응답 (Response)
- 성공: `201 Created` + `DiaryDto.DiaryResponse`

## 3. 비즈니스 로직 (요약)
1. `request.toCommand(userId)` → `diaryFacade.create(...)` → 저장.

## 4. 데이터 의존
- DB write: diaries
- Kafka: 일기 생성 이벤트 발행 가능성 (확인 필요)

## 5. 예외 케이스
- validation 실패 → 400

## 6. 암묵적 로직 (Implicit)
- 검증 결과(영역 validation)를 사전에 받아두고 만드는 흐름인지 확인 필요.
- 태그/카테고리 연관 처리 위치.

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] CreateRequest 필드 (텍스트, 이미지, 태그, 공개 여부)
- [ ] 검증 API와의 호출 순서

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
