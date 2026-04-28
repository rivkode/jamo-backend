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

## 9. KEEP/DROP/FIX 분류

**판정: FIX** (KEEP + 변경 사항 박제)

### KEEP 사유
- 실시간 입력 검증 UX (글쓰기 도중 라인별 피드백) 는 모바일 친화 — payload 가 작아 별 endpoint 로 두는 것이 검증 latency / 데이터 전송 모두 유리.
- contracts 측 별도 메서드 추가 없이 [`AiAssistantService.ValidateDiaryContent`](../../decisions/contracts/ai-assistant-service-method-catalog.md) 를 `lines=[text]` 단일 원소로 재사용 → contracts 표면적 최소.

### FIX 항목

상세 박제: [`docs/decisions/diary/validation-ai-fallback-policy.md`](../../decisions/diary/validation-ai-fallback-policy.md).

1. **동일 RPC 재사용 명시** — `validateLine` 도 `AiAssistantService.ValidateDiaryContent(lines=[text])` 호출. 별도 RPC (`ValidateLine`) 신설하지 않음. §3 비즈니스 로직에 명시.
2. **Rate limit + debounce 정책** — 키스트로크마다 호출 시 비용 폭발 위험. 클라이언트 debounce (입력 정지 후 500ms) + chat-service 측 사용자별 호출 quota (분당 60회). §8 의 "rate limit" Open Question 해소.
3. **응답 schema 에 lineIndex echo + status** — `LineResponse` 가 `lineIndex`, `status: VALID | INVALID | FAILED`, `issues: List<Issue>` 구조. 클라이언트가 라인별 표시 위치 매핑 가능. §2 명시화.
4. **Deadline 분리 정책** — `validate` 와 동일 chat-service Deadline 20s 사용 (contracts 일관). 단 클라이언트 측 타임아웃은 실시간 UX 고려 5s (입력 차단 X, 5s 후 silent fallback = 검증 우회). §6 에 명시.
5. **FAILED 시 fallback** — `validate` 와 동일 정책 (검증 우회) — 단 실시간 검증은 사용자에게 보이지 않게 silent 실패 (UI 변화 없음). §5 예외 케이스에 명시.
6. **userId propagation** — 사용량 quota 를 위해 chat-service 측에 userId 전달 (`validate.md` 와 동일).
7. **lineIndex 의미 박제** — 단순 클라이언트 echo (서버 검증 로직에 영향 없음, 응답에 그대로 반환). §3 에 명시.

### DROP 검토 후 KEEP 결정 근거
- `validate` 의 `lines: List<String>` 으로 단일 라인도 호출 가능 → endpoint 통합 검토 가능.
- **별 endpoint 유지 채택**: (a) 모바일 클라이언트 코드 분기 단순화 (실시간 vs 제출 분리), (b) chat-service 측 quota 정책 분리 (validateLine 은 quota 엄격 / validate 는 완화), (c) 모니터링 분리 (호출 빈도 / latency 다름). HTTP endpoint 분리 비용 < 통합 시 운영 복잡도.

### 영향 범위 (구현 PR 에서)
- diary-service: `DiaryValidationController.validateLine` + 동일 `ValidateDiaryService` 의 `validateSingleLine` 메서드 + 동일 `ValidateDiaryGrpcClient` (lines=[text] 호출).
- chat-service: 변경 없음 (validate 와 동일 RPC 핸들러).
- contracts: 변경 없음.
