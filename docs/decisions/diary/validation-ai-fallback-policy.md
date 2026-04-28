# Decision: validation 도메인 — AI 검증 정책 / 응답 schema / fallback / rate limit

- **상태**: Accepted
- **결정일**: 2026-04-28
- **결정자**: jonghun
- **PR**: PR D-a-1 (`feature/diary-prd-evaluation-validation`)
- **관련 PRD**: [`prd/validation/validate.md`](../../prd/validation/validate.md), [`prd/validation/validateLine.md`](../../prd/validation/validateLine.md)
- **관련 결정**: [`decisions/contracts/ai-assistant-service-method-catalog.md`](../contracts/ai-assistant-service-method-catalog.md) (status 카탈로그 박제)
- **관련 ADR**: [ADR-0003 AI 호출 아키텍처](../../adr/0003-ai-call-architecture.md)

## 컨텍스트

`validation/validate.md` 와 `validation/validateLine.md` 두 PRD 가 모두 `mined` 상태로 §9 평가 미완. 두 PRD 가 모두 chat-service 의 [`AiAssistantService.ValidateDiaryContent`](../contracts/ai-assistant-service-method-catalog.md) 메서드를 호출자 (Deadline 20s, status 카탈로그 `VALID/INVALID/FAILED`) 로 의존하는 구조이고, FIX 항목이 두 PRD 에 공통이라 단일 결정 로그로 박제한다.

[`ai-assistant-service-method-catalog.md`](../contracts/ai-assistant-service-method-catalog.md) 가 명시한 후속 검토 항목:

> `validation/validate.md` PRD 가 mined 상태 — 향후 KEEP/FIX/DROP 평가 시 `ValidateDiaryContent` 의 status 카탈로그 / mode 카탈로그 PRD 측 정합 검토.

본 결정이 이 검토를 해소한다.

## 결정

### 1. 두 endpoint 모두 동일 RPC 재사용

`POST /api/v1/diaries/validate` (multi-line) 와 `POST /api/v1/diaries/validate/line` (single-line) 모두 chat-service 의 `AiAssistantService.ValidateDiaryContent(lines)` 1개 RPC 를 호출. validateLine 은 `lines=[text]` 단일 원소로 호출. **별 RPC 신설하지 않음**.

근거:
- chat-service 측 검증 로직 (룰 + LLM mode) 동일.
- contracts 표면적 최소화 (메서드 1개로 2 use case 커버).
- HTTP endpoint 는 분리 유지 (모바일 클라이언트 코드 분기 단순화 / 모니터링 분리 / quota 정책 분리).

### 2. 응답 schema — status 카탈로그 명시

contracts 측 `ValidateDiaryContent` 응답이 `status: string` + `issues: List<LineIssue>` 구조.

| status | 의미 | HTTP 응답 |
|---|---|---|
| `VALID` | 모든 라인 통과 (`issues=[]`) | 200 + status=VALID |
| `INVALID` | 1+ 라인 실패 (`issues` 비어있지 않음) | 200 + status=INVALID + issues 배열 |
| `FAILED` | chat/ai 시스템 오류로 검증 미수행 | 200 + status=FAILED (fallback 분기) |

PRD 의 `DiaryValidationDto.ValidationResponse` / `LineResponse` 는 다음을 포함:

```
ValidationResponse {
  status: VALID | INVALID | FAILED,
  issues: [
    { lineIndex: int, code: string, message: string }
  ]
}

LineResponse {
  lineIndex: int,        // 클라이언트 echo
  status: VALID | INVALID | FAILED,
  issues: [
    { code: string, message: string }
  ]
}
```

### 3. FAILED 시 fallback 정책 — 검증 우회 (사용자 제출 허용)

| 시나리오 | UX |
|---|---|
| `validate` (제출 직전) FAILED | 사용자에게 안내 토스트 ("검증을 일시적으로 사용할 수 없어요. 그대로 저장됩니다.") + 일기 저장 허용 (검증 우회) |
| `validateLine` (실시간) FAILED | silent — 클라이언트가 무시 (UI 변화 없음). 사용자 입력 흐름 차단 X |

근거:
- 검증 실패 = 사용자 글쓰기 차단은 UX 손실이 큼. AI 의존 기능이 시스템 오류로 인한 사용자 차단을 일으키지 않는다는 일반 원칙 ([ADR-0003 §gRPC 운영 정책](../../adr/0003-ai-call-architecture.md) 의 fallback 정책 일치).
- 룰 위반 (`INVALID`) 은 사용자에게 명확히 표시 (글쓰기 차단), 시스템 오류 (`FAILED`) 는 우회.

### 4. mode 운영 정책 — 룰 1차 → LLM 2차 (chat-service 책임)

chat-service 의 `ValidateDiaryContent` 핸들러가:
1. 룰 검증 (offensive / banned word / 길이) 먼저 수행 — 라인이 룰 위반이면 즉시 `INVALID` + issues 반환 (LLM 호출 없음).
2. 룰 통과 시 LLM 호출 (한국어 자연스러움 / 부적절 표현 / 사실관계 등 룰로 못 잡는 영역) → 결과 종합.

근거:
- LLM 비용 절감 (룰로 잡히는 명백한 위반은 LLM 미호출).
- Latency 단축 (룰만 위반하는 케이스는 ~ms, LLM 미호출 시 Deadline 20s 안 씀).
- diary-service / PRD 는 mode 비공개 — chat-service 가 단독 결정.

mode 변경 (예: 룰만 사용 / LLM 만 사용) 시 본 결정 로그 갱신.

### 5. Rate limit + debounce 정책

| endpoint | 클라이언트 debounce | 서버측 quota (chat-service 기준) |
|---|---|---|
| `validate` (multi-line, 제출) | 없음 (사용자 명시 호출) | 사용자별 분당 10회 |
| `validateLine` (single, 실시간) | 입력 정지 후 500ms | 사용자별 분당 60회 |

quota 초과 시 chat-service 가 gRPC `Status.RESOURCE_EXHAUSTED` 반환 → diary-service 가 HTTP 429 매핑.

`validateLine` 의 quota 가 초과되면 클라이언트는 silent 무시 (입력 차단 X). `validate` 의 quota 초과는 사용자에게 명시적 안내 ("잠시 후 다시 시도해주세요").

근거:
- 키스트로크마다 호출 시 LLM 비용 폭발 위험. debounce 500ms 면 일반 타이핑 (~5 wpm) 에서 라인당 ~2-3 회 수렴.
- quota 분당 60회 = 평균 1초당 1회. 빠른 입력에서도 클라이언트 debounce 와 결합 시 충분.

### 6. Deadline 정책 — chat 측 통일, 클라이언트 측 분리

| 계층 | validate | validateLine |
|---|---|---|
| chat-service Deadline (gRPC) | **20s** ([catalog](../contracts/ai-assistant-service-method-catalog.md) 박제) | **20s** (동일) |
| diary-service → chat-service Deadline | **20s** (동일 박제) | **20s** (동일 박제) |
| 클라이언트 → diary-service 타임아웃 | **15s** (이후 fallback = 검증 우회) | **5s** (이후 silent fallback) |

근거:
- chat-service 의 RPC Deadline 은 catalog 박제 (단일 값). 호출자별 변형 X.
- 클라이언트 측 타임아웃은 UX 분리 — 실시간 (validateLine) 은 사용자 입력 흐름 막지 않도록 짧게.

### 7. userId propagation

chat-service 의 메서드별 사용량 카운터 / quota 가 사용자 단위이므로 다음 경로로 userId 전파:

```
diary-service Controller (@LoginUser)
  → ValidateDiaryService (Application)
    → ValidateDiaryGrpcClient.validate(userId, lines)
      → ValidateDiaryRequest { user_id, lines }
        → chat-service AiAssistantService.ValidateDiaryContent
```

PRD §6 의 "userId 시그니처에 있으나 facade 에 미사용" 부채는 본 결정으로 해소 (정책상 propagation 필수).

contracts 측 `ValidateDiaryRequest` 의 `user_id` field 는 이미 박제됨 (chat.proto 정의). proto 변경 불필요.

## 검토한 옵션 (요약)

### Option A. validateLine 을 별 RPC 로 — 거부
- contracts 표면적 증가, chat-service 핸들러 중복, mode 정책 분기 필요. 두 use case 의 검증 로직이 동일하므로 RPC 분리 이득 없음.

### Option B. validateLine endpoint 를 DROP, validate 로 통합 — 거부
- 모바일 클라이언트가 단일 endpoint 로 multi/single 분기 → 코드 복잡도 증가.
- monitoring / quota 정책 분리 어려움 (실시간 vs 제출 호출 패턴 다름).
- HTTP endpoint 분리 비용 < 통합 시 운영 복잡도.

### Option C. FAILED 시 사용자 차단 — 거부
- AI 의존 기능 오류로 사용자 글쓰기 차단은 UX 손실. ADR-0003 의 fallback 원칙 위반.

### Option D. mode 를 PRD / 클라이언트가 지정 — 거부
- mode 정책은 chat-service 의 비용 / 성능 trade-off 책임. 호출자가 지정하면 chat-service 의 정책 변경 자유도 ↓.

## 결과 및 영향

### 즉시
- `prd/validation/validate.md` §9: KEEP+FIX (본 결정 박제 7항목).
- `prd/validation/validateLine.md` §9: KEEP+FIX (본 결정 박제 7항목 + 동일 RPC 재사용 명시 + DROP 검토 후 KEEP 결정).

### 후속 (구현 PR 시점)

**diary-service**:
- `DiaryValidationController` (HTTP 진입점, 2 endpoint).
- `ValidateDiaryService` (Application, validate / validateSingleLine 2 메서드).
- `ValidateDiaryGrpcClient` (chat-service gRPC 호출, Deadline 20s, Circuit Breaker / Retry).
- `ValidationFallbackPolicy` (FAILED → 우회 결정 일관 처리).

**chat-service** (별도 PR):
- `AiAssistantGrpcService.ValidateDiaryContent` 핸들러 (룰 1차 → LLM 2차).
- 사용자별 quota 카운터 (Redis, 분당 10/60 회).
- LLM 호출은 ai-service `AiService.Complete` 경유 (ADR-0003).

**contracts**: 변경 없음 (이미 박제됨).

### 결정 대기 (본 결정에서 다루지 않음)
- 룰 카탈로그 (offensive / banned word / 길이) 의 구체 항목 — chat-service 구현 PR 시 결정.
- LLM 프롬프트 템플릿 — chat-service 구현 PR 시 결정.
- LineIssue 의 `code` enum 카탈로그 (예: `OFFENSIVE`, `LENGTH_EXCEEDED`, `UNNATURAL_KOREAN`) — chat-service 구현 PR 시 박제.
- 5xx 매핑 정책 (FAILED 를 200 응답으로 둘지 / RPC 5xx 변환할지) — `requestSentenceFeedback.md` §8 의 동일 Open Item 과 일괄 결정.

## 참고

- [`decisions/contracts/ai-assistant-service-method-catalog.md`](../contracts/ai-assistant-service-method-catalog.md) — `ValidateDiaryContent` status 카탈로그 박제
- [ADR-0003 AI 호출 아키텍처](../../adr/0003-ai-call-architecture.md) — chat-service 게이트웨이 책임 / fallback 원칙
- [ADR-0007 contracts-first 병렬 개발](../../adr/0007-contracts-first-parallel-development.md) — contracts 선행 후 코드 슬라이스 흐름
