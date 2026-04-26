---
api_id: validation.validate
http_method: POST
path: /api/v1/diaries/validate
auth: Y
controller: DiaryValidationController.kt
handler: validate
status: mined
---

# POST /api/v1/diaries/validate — 일기 전체 검증 (다중 라인)

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: `DiaryValidationDto.ValidateRequest { lines: List<String> }` (`@Valid`)

## 2. 응답 (Response)
- 성공: `200 OK` + `DiaryValidationDto.ValidationResponse`

## 3. 비즈니스 로직 (요약)
1. `validationFacade.validate(request.lines)` → 라인별 검증 결과 모음.

## 4. 데이터 의존
- 외부 API: 검증 모델(룰 기반 또는 LLM)

## 5. 예외 케이스
- validation → 400

## 6. 암묵적 로직 (Implicit)
- 일기 작성 전 사전 검증 흐름 — `POST /diaries`(create)와의 호출 순서 확인.
- userId가 시그니처엔 있으나 facade에 안 넘김 — 사용자 컨텍스트 무관 검증으로 추정.

## 7. 호출자 (Clients)
- 모바일/웹 (글쓰기 화면 실시간/제출 전 검증)

## 8. TODO / Open Questions
- [ ] 검증 룰 정의 위치
- [ ] LLM 사용 시 비용/지연

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
