# Service ↔ Domain Mapping

5개 Java 서비스 모듈 + 1개 Python 서비스 ↔ 13개 PRD 도메인 매핑 빠른 참조표. 결정 근거는 [ADR-0002 서비스 분할](../adr/0002-service-decomposition.md) + [ADR-0003 AI 호출 분리](../adr/0003-ai-call-architecture.md) 참조.

## 매핑 표

| 서비스 모듈 | 언어 | 포함 도메인 / 책임 | API 수 | MySQL 스키마 | 핵심 외부 의존 | 첫 배포 |
|---|---|---|---:|---|---|:---:|
| **identity-service** | Java | `auth`, `user`, `profile` | 14 | `identity` | 외부 IdP(KAKAO/NAVER/GOOGLE), Redis(토큰/검증번호), SMTP | ✅ |
| **diary-service** | Java | `diary`, `comment`, `validation`, `diarychat`, `sentence-feedback`(신규) | 21+ | `diary` | chat-service `AiAssistantService`(gRPC) | ✅ |
| **chat-service** | Java | `chat` (+ AI 비즈니스 게이트웨이 — 프롬프트 / 사용량 / rate limit / fallback) | 14 | `chat` | **ai-service `AiService`(gRPC)**, Redis(rate limit) | ✅ |
| **learning-service** | Java | `sentence`, `word` | 8 | `learning` | chat-service AI gateway(gRPC) | ❌ 비배포 |
| **platform-service** | Java | `shorts`, `event`(활동/랭킹), `feedback` | 3+ | `platform` | identity-service `UserSummaryService`(gRPC), Kafka(활동 이벤트 구독), Redis ZSET | ✅ |
| **ai-service** ⭐ | Python (FastAPI+grpcio) | 순수 LLM 추론 호출. chat-service 만 호출. 무상태 (ADR-0003) | 0 (proto 만) | (없음) | OpenAI API / vLLM / 자체 모델 | ✅ |

> `learning-service` 는 `settings.gradle.kts` 에 등록되지만 first deploy 제외 — 학습 기능 우선순위 상승 시 활성화.

## 의존 그래프

```
                        ┌─────────────────────────────────────────────┐
                        │   identity-service (Java)                   │
                        │   auth + user + profile                     │
                        │   (JWT 발급, JWKS 노출, UserSummary gRPC)    │
                        └────┬───────────────────┬───────────┬────────┘
                             │ JWT 검증           │ gRPC      │ Token Relay
                             │ (모든 서비스)        │           │
        ┌────────────────────┴──┐                 │           │
        ▼                       ▼                 │           │
┌──────────────────┐   ┌──────────────────┐       │           │
│ diary-service    │   │ chat-service     │◀──────┘           │
│ (Java)           │   │ (Java)           │                   │
│ diary+comment    │──▶│ chat +           │                   │
│ +validation      │   │ AI 비즈니스       │                   │
│ +diarychat       │   │ 게이트웨이         │                   │
│ +sentence-fb     │   │ (프롬프트/정책)    │                   │
└────────┬─────────┘   └────────┬─────────┘                   │
         │                      │ gRPC AiService             │
         │ Outbox/Kafka         ▼                             │
         │ (DiaryCreated,       ┌──────────────────┐          │
         │  CommentCreated, ..) │ ai-service      │          │
         ▼                      │ (Python)         │          │
┌──────────────────┐            │ FastAPI + grpcio │          │
│ platform-service │            │ 순수 LLM 추론     │          │
│ (Java)           │            │ (ADR-0003)       │          │
│ shorts+event     │            └────────┬─────────┘          │
│ +feedback        │                     │                    │
│ Redis ZSET 랭킹  │                     │ HTTPS / API        │
└──────────────────┘                     ▼                    │
         ▲                       ┌─────────────────┐          │
         └─── gRPC ──────────────┤ OpenAI/vLLM     │          │
              (UserSummary)      │ 자체 모델       │          │
                                 └─────────────────┘          │
                                                              │
[learning-service] (비배포 시작) ─── gRPC ────▶ chat-service ─┘
  sentence + word — 활성화 시
```

## Bounded Context 그룹 근거

| 그룹 | 근거 |
|---|---|
| identity | auth ↔ user ↔ profile 자격증명/정체성/프로필 응집 + 같은 MySQL 스키마 (ADR-0001) |
| diary | comment·validation·diarychat 모두 path가 `/diaries/...` 로 강결합 |
| chat (Java) | LLM/STT/TTS 호출의 비즈니스 진입점. PRD `chat`(14 API)이 자체로 응집도 높음. AI 추론(LLM+STT+TTS)은 ai-service 에 위임 (ADR-0003) |
| llm (Python) | 순수 LLM 추론 — Python LLM 생태계(LangChain, vLLM 등) 활용 위해 분리. 무상태 |
| learning | sentence + word 학습 컨텍스트. 단 sentence의 "문장 피드백" 기능은 diary-service 로 흡수 (ADR-0002) |
| platform | shorts(URL 큐레이션)·event(활동/랭킹)·feedback(사용자 의견) — 응집도 약하나 1인 운영 부담으로 임시 통합 |

## AI 호출 흐름 (ADR-0003 핵심)

```
[다른 Java 서비스] ──gRPC AiAssistantService──▶ [chat-service Java]
                                                       │ - 프롬프트 템플릿 적용
                                                       │ - 사용량/rate limit
                                                       │ - 사용자 컨텍스트 보강
                                                       │
                                                       │ gRPC AiService.complete
                                                       ▼
                                                [ai-service Python]
                                                       │
                                                       ▼
                                                [OpenAI / vLLM / 자체]
```

- **chat-service**: 비즈니스 의미 (`requestSentenceFeedback`, `validateDiaryContent`, `generateChatResponse` ...)
- **ai-service**: 일반화된 LLM 호출 (`complete`, 향후 `completeStream`)

## 패턴 등장 위치 (학습 케이스)

| 패턴 | 등장 위치 |
|---|---|
| Outbox | 모든 활동/도메인 이벤트 발행 |
| Saga (Choreography) | 회원 탈퇴 (identity → 4 Java 서비스 → identity. ai-service 는 무상태라 참여 X) |
| gRPC Server | chat-service `AiAssistantService` (Java), identity-service `UserSummaryService` (Java), **ai-service `AiService` (Python)** ⭐ |
| gRPC Client | diary/learning/diarychat → chat-service (Java↔Java), **chat-service → ai-service (Java↔Python)** ⭐, platform → identity (Java↔Java) |
| Read Model 동기화 | platform Redis ZSET 랭킹, (선택) identity 표시명 캐시 |
| Circuit Breaker / Deadline / Fallback | 모든 gRPC 클라이언트 호출. 특히 chat→llm 은 Deadline 30s + Retry 1회 |
| Token Relay | gRPC metadata `authorization: Bearer <accessJWT>` 전파. ai-service 까지 전파 여부는 ADR-0003 Open Item |

## 관련 문서

- 결정: [ADR-0001 인증 아키텍처](../adr/0001-authentication-architecture.md), [ADR-0002 서비스 분할](../adr/0002-service-decomposition.md), [ADR-0003 AI 호출 분리](../adr/0003-ai-call-architecture.md)
- 도메인 PRD: [`docs/prd/`](../prd/)
- contracts 카탈로그: [contracts-catalog.md](contracts-catalog.md)
- PRD 진행 트래커: [`docs/prd/_status.md`](../prd/_status.md)
