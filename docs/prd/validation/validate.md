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

## 9. KEEP/DROP/FIX 분류

**판정: FIX** (KEEP + 변경 사항 박제)

### KEEP 사유
- 일기 작성 사전 검증은 글쓰기 UX 의 핵심 흐름 — 제출 직전 (또는 명시적 검증 버튼) 시점 1회 호출하는 endpoint 자체 가치 충분.
- contracts 측 [`AiAssistantService.ValidateDiaryContent`](../../decisions/contracts/ai-assistant-service-method-catalog.md) 메서드가 본 PRD 의 use case 와 1:1 매핑 (Deadline 20s).

### FIX 항목

상세 박제: [`docs/decisions/diary/validation-ai-fallback-policy.md`](../../decisions/diary/validation-ai-fallback-policy.md).

1. **응답 schema 에 status 카탈로그 명시** — `ValidationResponse` 가 `status: VALID | INVALID | FAILED` + `issues: List<LineIssue>` 구조 (contracts `ValidateDiaryContent` 의 응답과 1:1 매핑). 현재 §2 의 `DiaryValidationDto.ValidationResponse` 추상 표현을 status 분기까지 명시화.
2. **FAILED 시 fallback 정책** — chat-service / ai-service 시스템 오류 → 검증 우회 (사용자 제출 허용) + 안내 메시지. 사용자 재시도 강제 X. UX 결정 박제.
3. **Deadline UX 처리** — chat-service 측 Deadline 20s. 클라이언트는 로딩 인디케이터 + 타임아웃 (15s) 후 fallback (검증 우회). §6 에 명시.
4. **호출 빈도 정의** — `validate` 는 제출 직전 1회 (또는 명시 버튼). 실시간 입력 도중 호출은 `validateLine` 책임 — `§6 암묵적 로직` 에 명시.
5. **mode 운영 정책** — 룰 (offensive / banned word) only / LLM / 혼합. 시작 시점 정책 박제 (chat-service 가 룰 1차 → 통과 시 LLM, 비용 절감).
6. **userId propagation** — chat-service 의 메서드별 사용량 카운터 / quota 를 위해 facade 에 userId 전파. 현재 §6 의 "userId 시그니처에 있으나 facade 에 안 넘김" 부채 해소.
7. **Open Questions 해소** — §8 의 "검증 룰 정의 위치" → chat-service 책임 (PRD context 안에서). "LLM 비용/지연" → mode 정책 + Deadline UX 로 해소.

### 영향 범위 (구현 PR 에서)
- diary-service: `DiaryValidationController` + `ValidateDiaryService` (Application) + `ValidateDiaryGrpcClient` (chat-service 호출).
- chat-service: `AiAssistantService.ValidateDiaryContent` 구현 (룰 + LLM mode 분기, userId 사용량 카운터).
- contracts: 변경 없음 (이미 박제됨).
