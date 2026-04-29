# Retrospective: sentence-feedback Batch 가 과설계 (PR #75)

- **상태**: Accepted (retrospective)
- **결정일**: 2026-04-29
- **결정자**: jonghun (사용자 발화)
- **관련 PR**: PR #75 (D-a-5-impl-batch, 머지됨)
- **선행 박제**:
  - [`decisions/diary/sentence-feedback-domain-policy.md`](sentence-feedback-domain-policy.md) §3 (TTL 24h) / §14 (90일 보존)
  - [`decisions/diary/sentence-feedback-batch-decisions.md`](sentence-feedback-batch-decisions.md) (15 결정 — 본 retrospective 후 일부 무효)

## 컨텍스트

PR #75 에서 sentence-feedback 의 4 batch 를 도입:

| Batch | 정책 |
|---|---|
| `SentenceFeedbackExpireBatch` | SUGGESTED → EXPIRED 전이 (5분 fixedDelay, 24h TTL) |
| `SentenceFeedbackCleanupBatch` | final 상태 90일 hard-delete (cron 02:00 KST) |
| `ProcessedEventCleanupBatch` | ProcessedEvent 30일 retention (cron 02:30 KST) |
| `PublishedOutboxCleanupBatch` | published Outbox 7일 retention (cron 03:00 KST) |

**박제 §3 (24h TTL) + §14 (90일 보존)** 가 PRD 평가 (#56) 에 박제되어 그대로 코드로 옮겨졌다.

## 사용자 발화 (2026-04-29)

> "이거 너무 과설계 같은데. 제안 수락이 이뤄지는 것은 사용자가 피드백을 받은 직후에 바로 선택하는거야. 이걸 뭐 몇시간 뒤에 수락하거나 그런게 아니야. 거래하는게 아니잖아."

## 분석

**sentence-feedback 의 실제 UX**:
1. 사용자가 일기 작성 중 한 문장 선택 → AI 제안 요청
2. AI 가 3개 대안 반환 (35s 이내)
3. **사용자가 즉시 (몇 초 ~ 몇 분 안에) ACCEPT 또는 REJECT 결정**
4. 결정 후 일기 작성 계속

이는 **즉시 결정 UX** — e-commerce 결제 / 회원 탈퇴 Saga / 외부 시스템 호출처럼 **장기 결정 / 비동기 워크플로 / 시간 차이가 발생하는 거래 도메인이 아니다**.

24h TTL / EXPIRED 전이 batch / 90일 보존은 **거래 도메인 패턴을 차용한 과설계**.

## 박제 항목별 retrospect

### §3 24h TTL + EXPIRED 전이 batch — 과설계

**박제 의도**: 사용자가 24h 안에 결정 안 하면 EXPIRED 로 정리 + 통계 정확성.

**실제 UX**: 사용자가 24h 동안 결정 안 하는 케이스가 거의 발생 안 함. 사용자는 일기 화면 닫을 때 이미 결정 완료. 24h 후에 살아있는 SUGGESTED row 자체가 **미사용 / 비정상 상태** — 운영 통계로 필요 없음.

**대안**:
- TTL 정책 자체 제거 — final 상태 도달 안 한 row 는 그대로 남음 (운영에서 outlier 만 모니터링)
- 또는 TTL 을 1주 / 1개월로 늘려 대부분 사용자 흐름 안에 결정 완료 보장 + 진짜 outlier 만 cleanup

**현재 처리**: PR #75 이미 머지. **운영 데이터 측정 후 batch 호출 횟수 / EXPIRED 전이 row 갯수 모니터링** 후 정량적 결정. EXPIRED 전이가 0 / 거의 0 이면 batch 제거 PR.

### §14 90일 보존 — 부분적으로 의미 있음

**박제 의도**: GDPR Art. 5(1)(e) / PIPA §21 — 개인정보 보유기간 제한.

**실제 UX vs 정책 분리**:
- GDPR/PIPA 자체는 의미 있음 — `original_sentence` (사용자 입력 자유 텍스트) 가 잠재 PII.
- 그러나 **회원 탈퇴 cascade 는 이미 PR #71** 의 `UserWithdrawalRequestedListener` Saga 가 처리 — 즉시 삭제.
- 일반 retention 90일 cleanup batch 는 "탈퇴 안 한 사용자의 final 상태 row 보존 윈도우" 만 다룸 — sentence-feedback 자체가 일기와 결합도 낮은 짧은 텍스트라 별 시스템 retention 정책 의무 약함.

**대안**:
- 별도 retention 미적용 — 사용자 탈퇴 cascade 만으로 GDPR/PIPA 정합 가능 (사용자의 권리 행사 윈도우 = 회원 탈퇴).
- 또는 retention 을 사용자 단위가 아닌 **diary-service 전체 정책** 으로 통합.

**현재 처리**: PR #75 이미 머지. 운영 / 법무 검토 후 결정.

### Outbox / ProcessedEvent retention 2종 — service-wide 책임 (정합 OK)

`PublishedOutboxCleanupBatch` (7일) + `ProcessedEventCleanupBatch` (30일) 은 **diary-service 인프라 전체** 의 책임. sentence-feedback 외 다른 sub-domain (validation / comment / diary core / diarychat) 도 같은 인프라 사용 — 단일 sub-domain 한정 X.

**다음 sub-domain 진입 시 주의**: 본 2 batch 는 이미 서비스 전체 책임 → **다른 sub-domain 에서 중복 도입 X**.

## 학습

### 1. 박제 → 코드 사이 UX 흐름 재검증 누락

PRD 평가 (#56) 시점에 §3 / §14 가 박제됐고, 코드 슬라이스 (#62 / #64 / #71 / #73 / #75) 진입 시 박제를 그대로 따랐다. **박제 → 코드 사이에 "이 정책이 실제 UX 에서 발생하는가?" 재검증 단계가 없었다**.

### 2. 거래 도메인 패턴의 default 차용

24h TTL / 90일 retention / EXPIRED batch 는 e-commerce / 결제 / 회원 탈퇴 Saga 같은 **거래 / 외부 시스템 / 비동기 워크플로** 도메인에서 의미 있는 패턴. sentence-feedback 같은 **즉시 결정 UX** 에는 default 가 아니다.

### 3. 다음 sub-domain 진입 시 적용

| 도메인 | 시간 정책 default 적합? |
|---|---|
| validation | **X** — 즉시 응답 (200 + status). batch 무관. |
| comment | **X** — 즉시 작성 / 삭제. retention 은 회원 탈퇴 cascade 로 충분. |
| diary core | **부분 X** — 일기 자체는 즉시. 단 diary-service 전체 retention (탈퇴 cascade) 만. |
| diarychat | **X** — 즉시 LLM 응답 + STT/TTS. 메시지 retention 은 별 정책. |
| chat (chat-service) | 도메인 별 검토. AI 호출 추적은 별 영역. |

## 새 프로세스 (memory 박제)

`memory/feedback_business_context_first.md`:
- code-planning 스킬 Step 1 직후, Step 2 진입 전 **"비즈니스 맥락 / 사용자 UX 흐름"** 1 절 추가
- 박제 §X 의 시간 정책 (TTL / retention / 만료) 이나 batch / cron 설계가 등장하면 **"이게 이 UX 에서 실제로 발생하는가?"** 사용자에 명시 확인
- 거래 / 외부 시스템 / 비동기 워크플로 (결제 / 회원 탈퇴 Saga 등) 가 아니면 시간 정책 / batch 는 default 가 아닌 **명시적 정당화 필요**

## 결과 및 영향

### 즉시
- 본 retrospective 박제 (`docs/decisions/diary/sentence-feedback-batch-retrospective.md`).
- `decisions/_index.md` 1행 추가.
- memory 2건 박제 (feedback / project) — 다음 세션 자동 인식.

### 후속 (운영 측정 후 결정)
- **별 PR**: SentenceFeedbackExpireBatch / SentenceFeedbackCleanupBatch 운영 데이터 측정 후 제거 / 정책 조정.
- ProcessedEvent / Outbox cleanup 2종은 service-wide 책임 — 다음 sub-domain 도입 시 중복 도입 X.

### Non-Goals
- PR #75 즉시 revert (사용자 발화 — 이대로 놔두고 retrospective 박제).
- 박제 §3 / §14 즉시 변경 (운영 측정 후 결정).

## 참고

- [PR #75 sentence-feedback Batch](https://github.com/rivkode/jamo-backend/pull/75) — 머지됨
- [`docs/decisions/diary/sentence-feedback-domain-policy.md`](sentence-feedback-domain-policy.md) §3 / §14
- [`docs/decisions/diary/sentence-feedback-batch-decisions.md`](sentence-feedback-batch-decisions.md) — 본 retrospective 후 일부 무효
- [memory: 기능 구현 전 비즈니스 맥락 먼저](https://internal/memory/feedback_business_context_first.md)
- [memory: sentence-feedback batch 과설계](https://internal/memory/project_sentence_feedback_batch_overengineered.md)
