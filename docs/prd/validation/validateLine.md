---
api_id: validation.validateLine
http_method: POST
path: /api/v1/diaries/validate/line
auth: Y
controller: DiaryValidationController.kt
handler: validateLine
status: mined
---

# POST /api/v1/diaries/validate/line — 일기 단일 라인 검증

## 1. 요청 (Request)
- Header: `@LoginUser`
- Body: `DiaryValidationDto.ValidateLineRequest { lineIndex, text }` (`@Valid`)

## 2. 응답 (Response)
- 성공: `200 OK` + `DiaryValidationDto.LineResponse`

## 3. 비즈니스 로직 (요약)
1. `validationFacade.validateLine(request.lineIndex, request.text)` → 단일 라인 검증.

## 4. 데이터 의존
- 외부 API: 검증 모델

## 5. 예외 케이스
- validation → 400

## 6. 암묵적 로직 (Implicit)
- 실시간 입력 도중 호출되는 endpoint 추정 — 호출 빈도가 높을 수 있음(rate limit / debounce 정책 확인).
- userId 시그니처에 있으나 facade에 미사용.

## 7. 호출자 (Clients)
- 모바일/웹 (실시간 검증)

## 8. TODO / Open Questions
- [ ] rate limit
- [ ] 라인 단위 검증의 비용 (LLM 호출 시)

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
