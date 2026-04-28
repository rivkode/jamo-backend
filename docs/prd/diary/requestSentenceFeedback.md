---
api_id: diary.requestSentenceFeedback
http_method: POST
path: /api/v1/diaries/sentence-feedback
auth: Y
controller: DiarySentenceFeedbackController.java
handler: requestFeedback
status: proposed
---

# POST /api/v1/diaries/sentence-feedback — 일기 문장 AI 피드백/제안

## 0. 배경
사용자가 일기를 작성하는 도중, 입력한 한 문장(**최대 50자**)에 대해 AI에게 피드백을 받아 더 자연스럽거나 의도에 맞는 대안 문장을 제안받는 기능. 일기는 짧은 문장 단위(통상 한 편당 약 3문장)로 작성되며 긴 본문이 아님. 기존 `validation/validateLine` 의 "맞춤법/형식" 수준을 넘어 **표현·맥락 수준의 대안 제안**과 **사용자의 수락/거부 결정 추적**이 목표.

## 0.1 도메인 라이프사이클 (SentenceFeedback Aggregate)

```
REQUESTED ─(AI 응답 수신)─▶ SUGGESTED ─┬─(사용자 수락)─▶ ACCEPTED (final)
                                       ├─(사용자 거부)─▶ REJECTED (final)
                                       └─(TTL 24h 무응답)─▶ EXPIRED (final)

REQUESTED ─(AI 호출 실패)─▶ FAILED (final, fallback 메시지 반환)
```

- **REQUESTED**: 사용자가 피드백을 요청한 직후 (AI 응답 수신 전)
- **SUGGESTED**: AI가 제안을 반환해 사용자에게 노출됨 (결정 대기)
- **ACCEPTED**: 사용자가 제안 중 하나를 채택 (후속 endpoint `acceptSentenceFeedback.md`)
- **REJECTED**: 사용자가 모든 제안을 거부 (후속 endpoint `rejectSentenceFeedback.md`)
- **EXPIRED**: 24h 내 결정 없음 (TTL 정책 — Open Item)
- **FAILED**: AI 호출 실패로 fallback 메시지만 반환된 상태

불변식: 상태 전이는 Aggregate 내부 메서드(`markSuggested`, `accept`, `reject`, `expire`, `markFailed`)로만 수행. final 상태에서 다른 상태로의 전이 금지. SUGGESTED → ACCEPTED/REJECTED 만 가능.

## 1. 요청 (Request)
- Header: `@LoginUser` (인증 필수)
- Body: `SentenceFeedbackRequest`
  - `sentence: String` (필수, **1~50자**)
  - `priorSentences: List<String>?` (선택, 같은 일기의 앞 문장. 각 항목 50자 이하, 최대 N개 — Open Item)
  - `tone: String?` (선택, 예: "casual", "formal" — Open Item)

## 2. 응답 (Response)
- 성공: `200 OK` + `SentenceFeedbackResponse`
  - `feedbackId: String` (UUID, 후속 accept/reject 호출의 식별자)
  - `status: String` (`SUGGESTED` | `FAILED`)
  - `originalSentence: String`
  - `suggestions: List<SentenceSuggestion>` (`suggestionId`, `text`, `reason`, `confidence`)
  - `issues: List<String>?`
  - `expiresAt: Instant` (24h 후 EXPIRED 전이 시점)
  - `processedAt: Instant`
- 실패: 400 (validation / 50자 초과), 401 (인증), 429 (rate limit), 502 (AI 오류), 504 (AI 타임아웃)

## 3. 비즈니스 로직 (요약)
1. 입력 길이(1~50자) / 금칙어 사전 검증
2. `SentenceFeedback` Aggregate 생성 (status=REQUESTED) + persist
3. diary-service `SentenceFeedbackService` → **chat-service AI gateway gRPC 호출** (`AiAssistantService.requestSentenceFeedback`, Deadline + Circuit Breaker)
4. 성공: AI 응답 → suggestions 매핑 → `Aggregate.markSuggested(suggestions, expiresAt)` → status=SUGGESTED
5. 실패: `Aggregate.markFailed(reason)` → status=FAILED + fallback suggestion 1개 반환
6. **`SentenceFeedbackRequested` 이벤트 Outbox 발행** → platform-service 활동 점수 가산

## 4. 데이터 의존
- DB write: `sentence_feedback` (diary 스키마)
  - `id, user_id, original_sentence, status, suggestions JSON, expires_at, created_at, decided_at, decision_suggestion_id, rejection_reason`
- gRPC: chat-service `AiAssistantService.requestSentenceFeedback`
- Redis: rate limit 카운터
- Kafka (Outbox): `SentenceFeedbackRequested`

## 5. 예외 케이스
- 빈 문장 / 50자 초과 → 400 (`SENTENCE_LENGTH_EXCEEDED`)
- 금칙어 → 400 (`SENTENCE_FORBIDDEN_CONTENT`)
- AI gateway 타임아웃 / Circuit Open → 200 + status=FAILED + fallback (5xx vs 200 fallback 정책 — Open Item)
- AI 응답 형식 오류 → 502
- 사용자별 호출 한도 초과 → 429

## 6. 암묵적 로직 (Implicit)
- 일기 저장 전(diaryId 없음) 호출 가능 — 작성 중 미리보기 흐름. Aggregate는 diaryId 없이도 생성됨.
- 50자는 한글/이모지 포함 시 산정 기준 명확화 필요 (Open Item)
- `priorSentences` 도 각 50자 제한 + 목록 상한
- AI 호출 비용 보호: 일일 사용자별 호출 한도 (Open Item)

## 7. 호출자 (Clients)
- 모바일/웹 (일기 작성 화면)

## 8. TODO / Open Questions
- [ ] 50자 산정 기준: code point vs char vs grapheme cluster
- [ ] EXPIRED TTL 24h 적정성
- [ ] AI 실패 시 200(FAILED) vs 5xx 정책
- [ ] 일일 호출 한도 정책
- [ ] AI gateway 응답 streaming 여부 (Phase B-3 결정)
- [ ] tone 옵션 enum 후보
- [ ] 언어 자동 감지 vs 명시 필드
- [ ] priorSentences 최대 개수
- [ ] SentenceFeedback 보존 기간 (개인정보 정책)

## 9. KEEP/DROP/FIX 분류

**KEEP+FIX** — [`decisions/diary/sentence-feedback-domain-policy.md`](../../decisions/diary/sentence-feedback-domain-policy.md) 박제 적용.

| 항목 | 결정 | 박제 § |
|---|---|---|
| ID 타입 | `feedbackId / userId / suggestionId` UUID 일관 (PRD `String` → UUID) | §1 |
| 라이프사이클 | REQUESTED → SUGGESTED → ACCEPTED/REJECTED + REQUESTED → FAILED + SUGGESTED → EXPIRED. final 전이 금지 | §2 |
| TTL | 24h KEEP (운영 모니터링 후 조정) | §3 |
| AI 호출 | chat-service `AiAssistantService.RequestSentenceFeedback` Deadline 35s ([catalog §28](../../decisions/contracts/ai-assistant-service-method-catalog.md)) | §6 |
| AI status 매핑 | `SUGGESTED / FAILED` (catalog 정합). FAILED → **200 + fallback 1건** (5xx X — validation 정합) — PRD §8 Open Item 해소 | §7 |
| 입력 검증 | `sentence` 1..50 **code points** (PRD §8 산정 기준 Open Item 해소) + 금칙어 도메인 invariant (LLM 강제 X) | §9 |
| `priorSentences` 상한 | **max 5**, 각 50 code points (PRD §8 Open Item 해소) | §9 |
| `tone` 옵션 | enum `casual / formal / neutral`, default null = `neutral` (catalog §147 후속 항목 해소) | §10 |
| Rate limit | 사용자별 일일 50회 / 분당 10회. 초과 → 429 (PRD §8 Open Item 해소) | §11 |
| 응답 schema | `SentenceFeedbackResponse` 7 필드 + `SentenceSuggestion` 4 필드 | §8 |
| diaryId | NULL 허용 (작성 전 미리보기 흐름) — PRD §6 정합 | §5 |
| 보존 기간 / GDPR | **90일** + `UserDataPurged` 즉시 삭제 (PRD §8 개인정보 정책 Open Item 해소) | §14 |
| `SentenceFeedbackRequested` 이벤트 | Outbox 발행 (구독자 = platform-service 활동 점수) | §12 |
| DiaryDeleted Saga cascade | diary-service 자체 cascade (`diary_id` = 삭제된 일기인 row hard-delete). NULL row 무관 | §13 |
| 선행 필요 contracts | `DiaryDeleted` + `SentenceFeedbackRequested/Accepted/Rejected` 4종 — D-b-1 PR | §16 |

후속 (Open Questions §8 해소): 50자 산정 / TTL / fallback / 일일 한도 / tone / priorSentences 상한 / 보존 기간 = §1~§14 박제로 모두 해소. streaming RPC 만 후속 (ADR-0003).


## 10. 후속 endpoint
- `POST /api/v1/diaries/sentence-feedback/{feedbackId}/accept` — `acceptSentenceFeedback.md`
- `POST /api/v1/diaries/sentence-feedback/{feedbackId}/reject` — `rejectSentenceFeedback.md`
