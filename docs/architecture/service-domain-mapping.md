# Service ↔ Domain Mapping

5개 서비스 모듈 ↔ 13개 PRD 도메인 매핑 빠른 참조표. 결정 근거는 [ADR-0002](../adr/0002-service-decomposition.md) 참조.

## 매핑 표

| 서비스 모듈 | 포함 도메인 | API 수 | MySQL 스키마 | 핵심 외부 의존 | 첫 배포 |
|---|---|---:|---|---|:---:|
| **identity-service** | `auth`, `user`, `profile` | 14 | `identity` | 외부 IdP(KAKAO/NAVER/GOOGLE), Redis(토큰/검증번호), SMTP | ✅ |
| **diary-service** | `diary`, `comment`, `validation`, `diarychat`, `sentence-feedback`(신규) | 21+ | `diary` | chat-service `AiAssistantService`(gRPC) | ✅ |
| **chat-service** | `chat` (+ AI gateway 책임) | 12 | `chat` | Python LLM 서버(gRPC), Redis(rate limit) | ✅ |
| **learning-service** | `sentence`, `word` | 8 | `learning` | chat-service AI gateway(gRPC) | ❌ 비배포 |
| **platform-service** | `shorts`, `event`(활동/랭킹), `feedback` | 3+ | `platform` | identity-service `UserSummaryService`(gRPC), Kafka(활동 이벤트 구독), Redis ZSET | ✅ |

> `learning-service` 는 `settings.gradle.kts` 에 등록되지만 first deploy 제외 — 학습 기능 우선순위 상승 시 활성화.

## 의존 그래프

```
                        ┌─────────────────────────────────────────────┐
                        │   identity-service                          │
                        │   auth + user + profile                     │
                        │   (JWT 발급, JWKS 노출, UserSummary gRPC)    │
                        └────┬───────────────────┬───────────┬────────┘
                             │ JWT 검증           │ gRPC      │ Token Relay
                             │ (모든 서비스)        │           │
        ┌────────────────────┴──┐                 │           │
        ▼                       ▼                 │           │
┌──────────────────┐   ┌──────────────────┐       │           │
│ diary-service    │   │ chat-service     │◀──────┘           │
│ diary+comment    │   │ chat             │                   │
│ +validation      │──▶│ + AI gateway     │                   │
│ +diarychat       │   │                  │──┐                │
│ +sentence-fb     │   └────────┬─────────┘  │ gRPC           │
└────────┬─────────┘            │            ▼                │
         │                      │       ┌─────────────────┐   │
         │ Outbox/Kafka         │       │ Python LLM 서버  │   │
         │ (DiaryCreated,       │       │ (외부)           │   │
         │  CommentCreated, ..) │       └─────────────────┘   │
         ▼                      │                             │
┌──────────────────┐            │                             │
│ platform-service │◀───────────┘                             │
│ shorts+event     │                                          │
│ +feedback        │──────────────── gRPC ────────────────────┘
│ Redis ZSET 랭킹  │   (UserSummary 조회, 랭킹 표시명용)
└──────────────────┘

[learning-service] (비배포 시작)
  sentence + word — chat-service gRPC 호출 (활성화 시)
```

## Bounded Context 그룹 근거

| 그룹 | 근거 |
|---|---|
| identity | auth ↔ user ↔ profile 자격증명/정체성/프로필 응집 + 같은 MySQL 스키마 (ADR-0001) |
| diary | comment·validation·diarychat 모두 path가 `/diaries/...` 로 강결합 |
| chat | LLM 호출 단일 진입점. PRD `chat`(12 API)이 자체로 응집도 높음 |
| learning | sentence + word 학습 컨텍스트. 단 sentence의 "문장 피드백" 기능은 diary-service 로 흡수 (ADR-0002) |
| platform | shorts(URL 큐레이션)·event(활동/랭킹)·feedback(사용자 의견) — 응집도 약하나 1인 운영 부담으로 임시 통합 |

## 패턴 등장 위치 (학습 케이스)

| 패턴 | 등장 위치 |
|---|---|
| Outbox | 모든 활동/도메인 이벤트 발행 |
| Saga (Choreography) | 회원 탈퇴 (identity → 4 서비스 → identity) |
| gRPC Client | diary/learning/diarychat → chat-service AI gateway, platform → identity, chat → Python LLM |
| gRPC Server | chat-service `AiAssistantService`, identity-service `UserSummaryService` |
| Read Model 동기화 | platform Redis ZSET 랭킹, (선택) identity 표시명 캐시 |
| Circuit Breaker / Deadline / Fallback | 모든 gRPC 클라이언트 호출 |
| Token Relay | gRPC metadata `authorization: Bearer <accessJWT>` 전파 |

## 관련 문서

- 결정: [ADR-0001 인증 아키텍처](../adr/0001-authentication-architecture.md), [ADR-0002 서비스 분할](../adr/0002-service-decomposition.md)
- 도메인 PRD: [`docs/prd/`](../prd/)
- contracts 카탈로그: [contracts-catalog.md](contracts-catalog.md)
- PRD 진행 트래커: [`docs/prd/_status.md`](../prd/_status.md)
