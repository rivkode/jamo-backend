# Decision: sentence-feedback 도메인 — 라이프사이클 / 404 통일 / 이벤트 / GDPR / 선행 필요 contracts

- **상태**: Accepted
- **결정일**: 2026-04-28
- **결정자**: jonghun
- **PR**: PR D-a-5 (`feature/diary-prd-evaluation-sentence-feedback`)
- **관련 PRD**: [`prd/diary/requestSentenceFeedback.md`](../../prd/diary/requestSentenceFeedback.md), [`acceptSentenceFeedback.md`](../../prd/diary/acceptSentenceFeedback.md), [`rejectSentenceFeedback.md`](../../prd/diary/rejectSentenceFeedback.md)
- **관련 결정**: [`decisions/diary/diary-domain-policy.md`](diary-domain-policy.md) (UUID / 404 IDOR / DiaryDeleted Saga), [`decisions/diary/comment-domain-policy.md`](comment-domain-policy.md) (404 통일 / Outbox), [`decisions/diary/validation-ai-fallback-policy.md`](validation-ai-fallback-policy.md) (FAILED 우회), [`decisions/diary/diarychat-domain-policy.md`](diarychat-domain-policy.md) (chat-service 게이트웨이 정합), [`decisions/contracts/ai-assistant-service-method-catalog.md`](../contracts/ai-assistant-service-method-catalog.md) (`RequestSentenceFeedback` Deadline 35s, status `SUGGESTED/FAILED`)
- **관련 contracts (이미 박제)**: chat.proto `AiAssistantService.RequestSentenceFeedback` + `SentenceFeedbackRequest/Response` (PR #34)
- **관련 contracts (선행 필요 — D-b-1 contracts PR)**:
  - `DiaryDeleted` Kafka 이벤트 — diary 도메인 박제 시점 미정의 ([diary-domain-policy §11](diary-domain-policy.md#11-diarydeleted-contracts-미정의--별도-contracts-pr)) + 본 결정 §13 cascade 트리거
  - `SentenceFeedbackRequested / SentenceFeedbackAccepted / SentenceFeedbackRejected` Kafka 이벤트 3종 — 본 결정 §12 발행
- **관련 ADR**: [ADR-0003 AI 호출 아키텍처](../../adr/0003-ai-call-architecture.md), [ADR-0007 contracts-first 병렬 개발](../../adr/0007-contracts-first-parallel-development.md)

## 컨텍스트

`sentence-feedback` 은 diary-service 안의 sub-도메인. 일기 작성 중 한 문장 (1~50자) 에 대한 AI 의 표현 / 맥락 수준 대안 제안 + 사용자 수락/거부 결정 추적이 핵심.

3 PRD 모두 **proposed** 상태 (그린필드 — mined 아님). 라이프사이클 / Aggregate 모델 / 이벤트 발행이 PRD §0.1 에 이미 정리되어 있으나, 권한 가드 / fallback / quota / 보존 기간 / Open Items 가 Phase D-a 박제 (validation / comment / diary / diarychat) 와 정합하도록 박제.

[`ai-assistant-service-method-catalog.md` §147 후속 검토 항목](../contracts/ai-assistant-service-method-catalog.md):

> `tone` 운영 enum 후보 (`casual` / `formal` / ...) — PRD `requestSentenceFeedback.md` §8 Open Question. 해소 시 `SentenceFeedbackRequest` 의 `reserved 6 to 9` 슬롯 활용.

본 결정이 이를 해소.

## 결정

### 1. ID 타입 — UUID 일관

| 자원 | 타입 |
|---|---|
| `feedbackId` | UUID (BINARY(16)) |
| `userId` (소유자) | UUID (identity-service 정합) |
| `suggestionId` | **UUID** (PRD `String` → UUID 박제) |
| `diaryId` | UUID? (작성 전 호출 시 NULL 허용 — §5) |

profile (#39) / comment (#50) / diary (#52) / diarychat (#54) 평가 정합.

### 2. Aggregate 라이프사이클 (KEEP)

```
REQUESTED ─(AI 응답 SUGGESTED)─▶ SUGGESTED ─┬─(accept)─▶ ACCEPTED  (final)
                                            ├─(reject)─▶ REJECTED  (final)
                                            └─(TTL 24h)─▶ EXPIRED  (final)

REQUESTED ─(AI 호출 FAILED)─▶ FAILED  (final, fallback 메시지 1건 반환)
```

| 항목 | 결정 |
|---|---|
| 상태 전이 메서드 | `markSuggested(suggestions, expiresAt) / accept(suggestionId, clock) / reject(reason, clock) / expire(clock) / markFailed(reason, clock)` |
| final 상태 전이 시도 | 409 (`SENTENCE_FEEDBACK_INVALID_TRANSITION`) |
| 알 수 없는 suggestionId 채택 | 400 (`SENTENCE_FEEDBACK_UNKNOWN_SUGGESTION`) |

PRD §0.1 그대로 KEEP.

### 3. TTL — 24h (KEEP)

PRD `requestSentenceFeedback §0.1` 의 24h TTL 그대로 채택. EXPIRED 전이는 배치 잡 또는 lazy (조회 시점) — 코드 슬라이스 시점 결정.

| 항목 | 결정 |
|---|---|
| TTL | 24h |
| 운영 모니터링 | 평균 사용자 결정 시간 / EXPIRED 비율 — 후속 조정 |

### 4. 권한 가드 — **403 → 404 통일 (FIX)**

PRD `accept §5` / `reject §5` 의 "다른 사용자 소유 → 403" 표기를 **404 통일** 로 변경.

| 시나리오 | HTTP |
|---|---|
| `feedbackId` 없음 | 404 |
| **다른 사용자 소유** (PRD: 403 → **404 IDOR**) | 404 |
| 이미 final 상태인 본인 feedback 재호출 | **409** (`SENTENCE_FEEDBACK_INVALID_TRANSITION`) — 정상 권한 + 비정상 상태, IDOR 아님 |
| 알 수 없는 suggestionId | 400 |
| 인증 누락 | 401 |
| 한도 초과 | 429 |

근거:
- comment §4 / diary §3 / diarychat §4 정합 — 자원 존재 비노출.
- 단 **state transition conflict** (이미 final 인 본인 feedback) 는 IDOR 위험 없음 (자원 자체는 본인 소유 — 존재 사실 노출되어도 무방) → 409 유지.

### 5. diaryId 미보유 허용 — 작성 전 미리보기

| 시나리오 | 처리 |
|---|---|
| 일기 저장 전 호출 | `diaryId` NULL 허용 (`SentenceFeedback.diaryId: UUID?`) |
| 일기 저장 후 호출 | `diaryId` 값 바인딩 (선택적, 본 결정 시점 미강제) |
| diaryId 인 경우 권한 검증 | diary 작성자 == feedback 소유자 검증 (404 IDOR) |

근거: PRD `request §6` 의 "작성 중 미리보기 흐름" 의도 보존. Aggregate 가 자체 식별자 기반 권한.

### 6. AI 호출 — chat-service 게이트웨이

```
diary-service requestFeedback
  → chat-service AiAssistantService.RequestSentenceFeedback (catalog §28, Deadline 35s)
    → ai-service AiService.Complete (LLM 추론)
  ← suggestions (3건 권장)
SentenceFeedback Aggregate.markSuggested(suggestions, expiresAt)
```

| 항목 | 결정 |
|---|---|
| 게이트웨이 | chat-service `AiAssistantService.RequestSentenceFeedback` ([catalog §28](../contracts/ai-assistant-service-method-catalog.md), 이미 박제) |
| Deadline | 35s ([catalog](../contracts/ai-assistant-service-method-catalog.md) §28) |
| 호출 흐름 | ADR-0003 — diary-service → chat-service → ai-service. diary-service 가 ai-service 직접 호출 X |

### 7. AI status 매핑 — `SUGGESTED / FAILED`

[`AiAssistantService.RequestSentenceFeedback`](../contracts/ai-assistant-service-method-catalog.md#각-메서드-status-카탈로그) status 박제 정합:

| status | 처리 |
|---|---|
| `SUGGESTED` | suggestions 1+ → `markSuggested()` → 200 + status=SUGGESTED |
| `FAILED` | fallback 메시지 1건 (예: "잠시 후 다시 시도해주세요") → `markFailed()` → **200 + status=FAILED** (5xx 미사용) |

근거 — validation §3 / diarychat §12 fallback 정합 (사용자 흐름 차단 지양).

PRD `request §8` 의 "AI 실패 시 200(FAILED) vs 5xx 정책" Open Item 해소 — **200 + status=FAILED** 채택.

### 8. 응답 schema

#### `SentenceFeedbackResponse` (7 필드)

```
{
  feedbackId: UUID,
  status: REQUESTED | SUGGESTED | ACCEPTED | REJECTED | EXPIRED | FAILED,
  originalSentence: String,
  suggestions: List<SentenceSuggestion>,    // FAILED 시 fallback 1건
  issues: List<String>?,                    // 도메인 invariant 위반 메시지 (옵션)
  expiresAt: Instant,                       // SUGGESTED 시 +24h, final 시 echo
  processedAt: Instant
}
```

#### `SentenceSuggestion` (4 필드)

```
{
  suggestionId: UUID,
  text: String,
  reason: String,
  confidence: Double                        // 0.0 ~ 1.0
}
```

#### `accept` 응답 — 동일 `SentenceFeedbackResponse` (status=ACCEPTED, `decisionSuggestionId` / `decidedAt` 포함)

#### `reject` 응답 — `204 No Content` (PRD KEEP)

### 9. 입력 검증 — 도메인 invariant 만, LLM 강제 X

| 검증 | 결정 |
|---|---|
| `sentence` 길이 | **1..50 code points** (PRD §8 산정 기준 Open Item 해소 — code point 채택, char/grapheme 미사용) |
| `priorSentences` 항목 길이 | 각 1..50 code points |
| `priorSentences` 개수 상한 | **max 5** (PRD §8 Open Item 해소) |
| 금칙어 | 도메인 invariant 만 (간단한 deny list — 코드 슬라이스 시점 정의). LLM 강제 검증 X |
| `tone` 화이트리스트 | §10 박제 |

근거 — validation 의 "서버 강제 LLM 검증 X" 정합 (LLM 비용 / Deadline UX). 룰 위반은 도메인 invariant.

### 10. tone 옵션 — enum 박제

| 값 | 의미 |
|---|---|
| `casual` | 친근한 표현 |
| `formal` | 정중한 표현 |
| `neutral` | 중립 표현 (default 처리) |
| `null` (미명시) | 클라가 명시하지 않음 — chat-service 가 default 정책 적용 (현재는 `neutral` 와 동등) |

| 항목 | 결정 |
|---|---|
| 클라 default | null (미명시) |
| 서버 정책 | null = `neutral` 로 처리 (chat-service 측) |
| 확장 | 새 값 추가는 본 결정 갱신 + chat.proto reserved 슬롯 (catalog §147) |

[catalog §147](../contracts/ai-assistant-service-method-catalog.md) 후속 검토 항목 해소. contracts 변경은 D-b-1 contracts PR 시점.

### 11. Rate limit / quota

| 항목 | 결정 |
|---|---|
| 사용자별 일일 호출 한도 | **50회** (잠정) |
| 사용자별 분당 한도 | **10회** |
| 초과 시 | HTTP 429 (`RATE_LIMITED`) |
| chat-service 측 quota | 동일 정책 — 호출 측 (diary-service) 가 1차 카운터, gateway 가 2차 |

PRD `request §8` 의 "일일 호출 한도 정책" Open Item 해소.

### 12. 이벤트 발행 — 3종 모두 Outbox

| 이벤트 | 시점 | 구독자 |
|---|---|---|
| `SentenceFeedbackRequested` | request 트랜잭션 (Aggregate 저장 + Outbox insert) | platform-service (활동 점수 가산) |
| `SentenceFeedbackAccepted` | accept 트랜잭션 | platform-service (수락 가중 점수) |
| `SentenceFeedbackRejected` | reject 트랜잭션 | (현 시점 미정 — 학습 분석 후속) |

PRD `reject §8` 의 "REJECTED 이벤트 발행 여부" Open Item 해소 — **발행 채택**.

근거:
- 발행 비용 (Outbox row + Kafka) 대비 학습 신호 / 분석 가치 큼.
- 3종 통일성 — 라이프사이클 final 전이는 모두 이벤트 발행. 후속 추가 구독자 (분석 / 알림) 도입 시 별도 갱신 불요.
- 멱등성: `eventId` UUID 키 + 구독자 측 `ProcessedEvent` 테이블 ([CLAUDE.md](../../../CLAUDE.md) 의무).

### 13. DiaryDeleted Saga cascade — diary-service 자체 cascade

| 항목 | 결정 |
|---|---|
| diary 삭제 시 처리 | `sentence_feedback` 중 `diary_id` = 삭제된 일기 인 row 만 hard-delete cascade |
| `diaryId` NULL row | 무관 (작성 전 호출분 — diary 와 결합 X). 별도 GDPR / TTL 로 정리 (§14) |
| 멱등성 | `ProcessedEvent` 테이블 |
| Saga 책임 | diary-service 가 `DiaryDeleted` 발행자이자 자체 구독자 (sentence-feedback 영역) |

[diary-domain-policy §10 cascade 명세](diary-domain-policy.md#diarydeleted-구독자-cascade-명세-각-도메인-평가-시점-정합) 의 "learning-service: sentence-feedback 정리 (D-a-5 시점 정합)" 후속 의무 해소 — sentence-feedback 은 diary-service 가 흡수 ([_status.md](../../prd/_status.md) 도메인 매핑) 이므로 diary-service 자체 cascade.

### 14. 보존 기간 / GDPR

| 항목 | 결정 |
|---|---|
| 일반 보존 기간 | **90일** (잠정) — final 상태 도달 시점부터 90일 후 hard-delete |
| 사용자 회원 탈퇴 시 | **즉시 삭제** — identity-service 가 발행한 `UserWithdrawalRequested` Kafka 이벤트 ([PR #36 박제](../../architecture/contracts-catalog.md)) 를 **구독**해 사용자 소유 모든 sentence_feedback row hard-delete + `UserDataPurged` (`sourceService="diary"`) **회신 발행** (Saga). identity-service 는 모든 회신 수신 시 User HARD DELETE — 자세한 흐름은 [`UserWithdrawalRequested` JavaDoc](../../../contracts/src/main/java/app/backend/jamo/contracts/event/identity/UserWithdrawalRequested.java) / [`UserDataPurged` JavaDoc](../../../contracts/src/main/java/app/backend/jamo/contracts/event/identity/UserDataPurged.java) |
| `diaryId` NULL 인 row | 일반 90일 정책 적용 (작성 전 호출분도 동일) |
| 운영 | 배치 잡 (일 1회 — 코드 슬라이스 시점) |

PRD `request §8` 의 "SentenceFeedback 보존 기간 (개인정보 정책)" Open Item 해소.

> **2026-04-29 정정 (D-a-5-impl-infra)**: 본 §14 의 초기 박제 ("`UserDataPurged` 이벤트 **구독**") 가
> contracts JavaDoc 의 흐름과 의미가 달랐음 — `UserDataPurged` 는 4 도메인 서비스가 identity-service
> 에 회신하는 이벤트 (sourceService 로 발행 서비스 식별). diary-service 는 회신을 발행하는 측이지
> 구독하는 측이 아님. 정정 — diary-service 는 `UserWithdrawalRequested` 를 구독하고
> `UserDataPurged` (sourceService="diary") 를 회신 발행한다.

### 15. 일기 본문 자동 반영 — 클라 책임

| 항목 | 결정 |
|---|---|
| ACCEPTED 후 일기 본문 반영 | **클라이언트 책임** (서버 미반영) |
| 다중 suggestion 동시 채택 | 미지원 (Non-Goals — 단일 suggestion 만) |
| 재요청 / 재시도 | final 상태 후 새 요청 = 새 `feedbackId` (별 Aggregate). 재시도 미지원 |

근거:
- 서버 측 자동 반영은 일기 작성 트랜잭션 결합 강화 — UX 단순화 위해 클라 책임.
- diary `create` 트랜잭션과 sentence-feedback 트랜잭션 분리 (independent).

PRD `accept §8` 의 "일기 본문 자동 반영 vs 클라이언트 책임" Open Item 해소.

### 16. 선행 필요 contracts (D-b-1 PR 일괄 박제)

| 항목 | 영향 |
|---|---|
| `DiaryDeleted` Kafka 이벤트 record | diary-domain-policy §11 미해소 + 본 결정 §13 cascade 트리거 |
| `SentenceFeedbackRequested` record | 본 결정 §12 |
| `SentenceFeedbackAccepted` record | 본 결정 §12 |
| `SentenceFeedbackRejected` record | 본 결정 §12 |

**별 contracts PR (D-b-1 권장)**: 4 record 신설 + [`contracts-catalog.md`](../../architecture/contracts-catalog.md) §도메인 이벤트(diary) 4 행 ✅ 전환 + JavaDoc (발행자 / 구독자 / 토픽) + 단위 테스트 (PR #36 패턴 정합 — `EventFields.requireNonBlank/NonNull/NonNegative` 검증).

본 평가 PR (D-a-5) 시점 contracts 변경 0. 본 PR 본문에 후속 명시.

추가 — `tone` 운영 enum 도입 시 `SentenceFeedbackRequest` 의 `reserved 6 to 9` 슬롯 활용 ([catalog §147](../contracts/ai-assistant-service-method-catalog.md)). 본 결정 §10 박제로 D-b-1 contracts PR 시점에 chat.proto 갱신 가능.

## 검토한 옵션 (요약)

### Option A. 권한 가드 403 유지 — 거부

PRD 원안 그대로. 단점:
- comment / diary / diarychat 의 404 통일 정책과 불일치.
- IDOR — 다른 사용자가 feedbackId 추측 후 403 받으면 자원 존재 노출.

### Option B. final 상태 충돌도 404 — 거부

장점: 모든 비정상 상태 = 404 일관.
단점: 본인 소유 자원의 정상 권한 호출 흐름에서 디버깅 어려움 (개발자 / 클라가 "왜 안 되지?" 확인 불가). state transition conflict 는 IDOR 위험 없음 → 409 가 적절.

### Option C. SentenceFeedbackRejected 이벤트 미발행 — 거부

장점: Outbox / Kafka 트래픽 절감.
단점: 학습 신호 손실 (어떤 제안이 거부되는지 분석 불가). 라이프사이클 final 전이 3종 통일성 손상.

### Option D. tone 미박제 (운영 후 결정) — 거부

장점: 운영 데이터 기반 결정.
단점: catalog §147 의 후속 검토 항목 미해소 — proto reserved 슬롯 채택 시점 결정 지연. enum 으로 박제하되 후속 값 추가는 본 결정 갱신으로 충분.

### Option E. 일기 저장 자동 반영 (서버 책임) — 거부

장점: 클라 단순화.
단점: diary `create` 트랜잭션 결합 강화. ACCEPTED 후 사용자가 다른 내용으로 일기 저장 시 자동 반영이 의도와 충돌.

### Option F. 다중 suggestion 동시 채택 — 거부 (Non-Goals)

PRD `accept §8` 의 "현재는 단일" 명시. 다중 채택은 일기 본문 합성 / 순서 결정 등 추가 정책 필요 → 후속.

### Option G. 50자 산정 = char (UTF-16 code unit) — 거부

이모지 / 한자 surrogate pair 경계에서 글자 수 불일치. **code point** 가 표준 (Java `String.codePointCount`).

### Option H. 보존 기간 = 무기한 — 거부

GDPR / 개인정보 정책 위반. 90일 잠정.

## 결과 및 영향

### 즉시

- 3 PRD §9 채움 (모두 KEEP+FIX, 본 결정 박제 cross-reference).
- diary-domain-policy 의 "learning-service: sentence-feedback 정리 (D-a-5 시점 정합)" 후속 의무 해소 — diary-service 자체 cascade 박제.
- ai-assistant-service-method-catalog §147 의 `tone` 운영 enum 후속 검토 항목 해소.
- PRD §8 Open Item 9건 해소 (50자 산정 / TTL / fallback / 일일 한도 / tone / priorSentences 상한 / Rejected 이벤트 / 보존 기간 / 일기 자동 반영).

### 후속 PR 시리즈

```
D-b-1 (contracts)               : DiaryDeleted + SentenceFeedback*3 = 4 Kafka record 일괄 박제 + contracts-catalog 갱신 + (선택) tone enum reserved 슬롯 채택
D-a-5-impl-domain               : SentenceFeedback aggregate (UUID id / Status enum / SentenceText VO 50 code points / Tone enum / Suggestion VO) + Repository port + 도메인 예외
D-a-5-impl-app                  : 3 Application Service (Request / Accept / Reject) + chat-service gRPC client port + Outbox 발행 어댑터
D-a-5-impl-infra                : SentenceFeedbackJpaEntity + Mapper + chat-service gRPC client 어댑터 + DiaryDeleted Consumer (자체 cascade) + UserDataPurged Consumer (GDPR) + Flyway V6 (sentence_feedback + processed_event)
D-a-5-impl-presentation         : DiarySentenceFeedbackController + DTO + ExceptionHandler + WebMvcTest
D-a-5-impl-batch                : EXPIRED 전이 배치 잡 + 90일 보존 cleanup 잡
```

### 결정 대기 (본 결정에서 다루지 않음)

- 금칙어 사전 (deny list) 의 정확 항목 — 코드 슬라이스 시점.
- AI 응답의 suggestions 개수 정책 (현재 잠정 3건) — chat-service 구현 PR.
- AI 응답 streaming RPC 도입 — ADR-0003 후속.
- LLM 프롬프트 템플릿 (tone 별) — chat-service 구현 PR.
- ACCEPTED / REJECTED 점수 가중치 정확값 — platform-service 구현 PR.
- EXPIRED 전이 배치 vs lazy 평가 — D-a-5-impl-batch.
- 90일 보존 잠정값 운영 데이터 후 조정 — 후속.
- 일일 50회 / 분당 10회 quota 잠정값 — 운영 후 조정.

### Non-Goals

- 일기 본문 자동 반영 (서버 책임 X).
- 다중 suggestion 동시 채택.
- ACCEPTED 시 일기 저장 자동 트리거.
- Streaming RPC.
- 제안 재요청 / 재시도 (final 상태 후 새 요청 = 새 feedbackId).
- 사용자별 제안 캐싱.
- 제안 품질 ML 학습 자체 (Rejected 이벤트 활용은 후속 분석 PR).
- diaryId 강제 바인딩 (저장 전 호출 허용).

## 참고

- [`prd/diary/requestSentenceFeedback.md`](../../prd/diary/requestSentenceFeedback.md) §9
- [`prd/diary/acceptSentenceFeedback.md`](../../prd/diary/acceptSentenceFeedback.md) §9
- [`prd/diary/rejectSentenceFeedback.md`](../../prd/diary/rejectSentenceFeedback.md) §9
- [`decisions/diary/validation-ai-fallback-policy.md`](validation-ai-fallback-policy.md) — FAILED 우회 / userId propagation
- [`decisions/diary/comment-domain-policy.md`](comment-domain-policy.md) — 404 통일 / Outbox
- [`decisions/diary/diary-domain-policy.md`](diary-domain-policy.md) — DiaryDeleted Saga cascade
- [`decisions/diary/diarychat-domain-policy.md`](diarychat-domain-policy.md) — chat-service 게이트웨이 정합
- [`decisions/contracts/ai-assistant-service-method-catalog.md`](../contracts/ai-assistant-service-method-catalog.md) — `RequestSentenceFeedback` Deadline 35s / status / tone 후속 §147
- [`docs/architecture/contracts-catalog.md`](../../architecture/contracts-catalog.md) — DiaryDeleted / SentenceFeedback*3 미작성 표기
- [ADR-0003 AI 호출 아키텍처](../../adr/0003-ai-call-architecture.md)
- [ADR-0007 contracts-first 병렬 개발](../../adr/0007-contracts-first-parallel-development.md)
- [CLAUDE.md](../../../CLAUDE.md) — Outbox 의무 / Kafka Consumer 멱등 / 404 IDOR
- Chris Richardson, *Microservices Patterns* (Saga, Outbox)
