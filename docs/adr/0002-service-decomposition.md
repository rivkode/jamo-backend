# ADR-0002: 서비스 분할

- **상태**: Accepted
- **결정일**: 2026-04-26
- **결정자**: jonghun

## 컨텍스트
PRD 13개 도메인 / ~62 API 그린필드 MSA. ADR-0001에서 인증은 별도 auth 책임으로 분리하되 user/profile과 같은 MySQL 스키마를 공유하기로 결정. 1인 운영 + 학습 목적 강조 + 도메인 결합도 분석 결과를 바탕으로 서비스 경계를 확정해야 함. PRD 분석에서 다음 결합이 식별됨: comment·validation·diarychat ↔ diary 강결합, auth ↔ user ↔ profile 강결합, chat AI = diarychat AI = validation AI = sentence 피드백 (모두 같은 Python LLM 서버 호출). 회원 탈퇴 시 모든 도메인 데이터 일괄 삭제 정책 존재. 단일 MySQL 인스턴스 + 스키마 격리 정책 결정됨. event 도메인은 단순 수집을 넘어 사용자 활동 점수 누적 + Redis Sorted Set(ZSET) 기반 전체 랭킹 제공 책임이 있음.

## 검토한 옵션

### Option A: 13개 도메인 = 13개 서비스 (1:1)
- **장점**: 도메인 경계 가장 선명
- **단점**: 운영 비용 폭발(13 배포·CI), 데이터 일관성 처리 폭증, 30 API 규모에 과분할
- **적합성**: 비추 — 1인 운영 불가능, 단일 목적 도메인까지 서비스로 띄울 가치 없음

### Option B: 응집도 기반 5개 서비스 (채택)
- identity-service(auth+user+profile), diary-service(diary+comment+validation+diarychat), chat-service(chat+AI gateway), learning-service(sentence+word), platform-service(shorts+event+feedback)
- **장점**: Bounded Context 정합성 높음, AI 호출이 단일 서비스에 집중, 30 API에 적정. 학습 목적의 분산 시스템 인프라(gRPC/Kafka/Saga/Outbox/Read Model)가 모두 실제 흐름에서 등장
- **단점**: platform-service의 shorts/feedback 응집도 약함, learning-service 현재 미사용, 5 배포 파이프라인 부담
- **적합성**: 학습 가치 + 도메인 정합 + 1인 운영 부담 사이의 균형점

### Option C: 응집도 기반 3개 서비스 (더 통합)
- identity, content(diary+학습+shorts), chat(+platform)
- **장점**: 1인 운영 가장 가벼움
- **단점**: content 안에 일기/학습/숏츠 잡탕, 향후 분할 비용
- **적합성**: 분리 가치(학습 목적)가 우선이라는 사용자 결정과 충돌

### Option D: Spring Modulith (모놀리식 모듈러)
- 1개 배포, 모듈만 13개로 분리
- **장점**: 운영 가장 단순
- **단점**: 사용자 결정(멀티모듈 MSA)과 충돌, gRPC/Kafka 인프라 도입 의도 무력화
- **적합성**: 비교군으로만 제시. 사용자 결정과 충돌

## 결정

**Option B 채택.**

| 서비스 | 포함 도메인 | API 수 | MySQL 스키마 |
|---|---|---:|---|
| identity-service | auth + user + profile | 14 | `identity` |
| diary-service | diary + comment + validation + diarychat | 21+ | `diary` |
| chat-service | chat (+ AI/LLM gateway 책임) | 12 | `chat` |
| learning-service | sentence + word (모듈 생성, 첫 단계 비배포) | 8 | `learning` |
| platform-service | shorts + event(활동/랭킹) + feedback | 3+ | `platform` |

### 핵심 정책

1. **AI/LLM 단일 진입점 = chat-service**
   - Python LLM 서버에 대한 gRPC 호출은 chat-service만 수행
   - 다른 서비스가 AI가 필요하면 chat-service의 `AiAssistantService` gRPC 를 호출
   - 모델/프롬프트/비용/캐싱/rate limit 정책을 한 곳에 격리

2. **sentence 피드백 흐름 (PRD `docs/prd/diary/{request,accept,reject}SentenceFeedback.md`)**
   - diary 작성 시 문장 단위 AI 피드백은 diary-service → chat-service gRPC 호출. learning-service 경유 X
   - diary-service 안에 `sentence-feedback` 도메인 추가. Aggregate 라이프사이클: REQUESTED → SUGGESTED → ACCEPTED/REJECTED/EXPIRED/FAILED
   - 문장 길이 제한 50자
   - learning-service는 학습 이력/단어장 관리 같은 자기 책임에 집중 (활성화 후)

3. **learning-service 비배포 시작**
   - `settings.gradle.kts` 에는 등록되지만 first deploy 제외
   - 학습 기능 우선순위 상승 시 활성화

4. **platform-service의 event 도메인 = 활동 추적 + 랭킹 Read Model**
   - 각 서비스가 Outbox 패턴으로 활동 이벤트(`ActivityHappened{userId, type, points, occurredAt, eventId}`) 발행
   - platform-service가 구독 → 멱등성 검증(`ProcessedEvent`) → 점수 정책 적용 → **Redis ZSET `ranking:global` 갱신** (`ZINCRBY`)
   - 랭킹 조회는 ZSET 직접 read (`ZREVRANGE 0 99 WITHSCORES`). 사용자 표시명은 identity-service `UserSummaryService` gRPC 호출 또는 Redis 캐시 Read Model
   - 활동 취소(예: 일기 삭제) 시 역방향 이벤트로 점수 차감 — Saga 아닌 단순 정정 이벤트
   - SoT는 Kafka 이벤트 로그(또는 각 서비스 활동 데이터). ZSET 유실 시 재구축 배치 필수

5. **platform-service의 shorts/feedback은 임시 통합**
   - 응집도 약함을 인정하고 묶음
   - 도메인 성장 시 재분할

6. **DB 정책**
   - 단일 MySQL 인스턴스 + 스키마 5개 (서비스 1:1)
   - 서비스간 직접 JOIN/쿼리 금지 (`module-boundary` 원칙)
   - 같은 인스턴스라도 별도 DB user/credential 권장

7. **회원 탈퇴 Saga (Choreography)**
   - identity-service `UserWithdrawalRequested` (Outbox) 발행
   - diary/chat/learning/platform 4 서비스가 구독 → 자기 도메인 데이터 삭제 → `UserDataPurged.{service}` 회신
   - identity-service가 4개 회신 모두 수신 시 User HARD DELETE
   - 일정 시간 내 미회신 → 운영 알림 + 수동 정리 (자동 보상 정책은 후속 ADR)

8. **외부 진입점**
   - ADR-0001 결정대로 Spring Cloud Gateway 미도입
   - 클라이언트가 서비스별 endpoint 직접 호출 또는 인프라 단(ALB/Nginx) L7 라우팅

### MSA 패턴 사용 매핑 (학습 케이스 확보 점검)

| 패턴 | 등장 위치 |
|---|---|
| **Outbox** | 모든 도메인 이벤트 발행 — `DiaryCreated`, `CommentCreated`, `DiaryLiked`, `ChatGenerated`, `VoiceInputProcessed`, `ActivityHappened`, `SentenceFeedbackRequested/Accepted/Rejected`, `UserWithdrawalRequested`, `UserDataPurged.*` 등 |
| **Kafka Consumer + 멱등성(`ProcessedEvent`)** | platform-service `ActivityListener`, identity-service `UserDataPurgedListener`, 등 모든 구독 지점 |
| **Saga (Choreography)** | 회원 탈퇴 (identity → 4 서비스 → identity 최종 삭제) |
| **gRPC (server)** | chat-service `AiAssistantService`, identity-service `UserSummaryService` |
| **gRPC (client)** | diary-service / learning-service / diarychat → chat-service AI gateway. platform-service → identity-service. chat-service → Python LLM 서버 |
| **Circuit Breaker / Deadline / Retry / Fallback** | 모든 gRPC 클라이언트 호출 (Resilience4j) |
| **Read Model 동기화** | platform-service Redis ZSET 랭킹, (선택) identity 표시명 캐시 |
| **Token Relay** | gRPC metadata 로 사용자 access JWT 전파 (ADR-0001) |

### Gradle 모듈 구조

```
:contracts                    # proto + Kafka 이벤트 (서비스간 공유 계약)
:common-auth-jwt              # JWT 검증 라이브러리 (ADR-0001)
:common-infrastructure        # MySQL/Redis/Kafka 공통 설정
:identity-service             # auth + user + profile
:diary-service                # diary + comment + validation + diarychat + sentence-feedback
:chat-service                 # chat + AI gateway
:learning-service             # sentence + word (비배포로 시작)
:platform-service             # shorts + event(활동/랭킹) + feedback
```

## 결과 및 영향

### 긍정적
- AI 호출이 chat-service 단일 진입점에 집중되어 모델/비용/프롬프트 변경의 영향 격리
- 활동 점수/랭킹이 Outbox + Kafka + Redis ZSET Read Model로 자연스럽게 매핑 — `module-boundary` 패턴 학습 케이스 확보
- 멀티모듈 MSA 학습 목적과 정합 (Saga, Outbox, gRPC, Read Model 동기화 모두 실전 적용 가능)
- 회원 탈퇴 같은 cross-service 흐름이 Choreography Saga로 자연스럽게 매핑
- learning-service 비배포 시작으로 1인 운영 부담 일부 완화
- 단일 MySQL 인스턴스 + 스키마 격리로 인프라 비용 최소화

### 부정적 / 트레이드오프
- 5 배포 파이프라인 + 5 서비스 모니터링 = 1인 운영 부담
- platform-service의 shorts/feedback은 응집도 약함 (임시 통합 명시)
- AI 호출이 chat-service에 집중되어 chat-service 장애 = 전 서비스의 AI 기능 다운 (Circuit Breaker로 완화)
- 단일 MySQL 인스턴스 = 인스턴스 장애 = 모든 서비스 영향 (격리 X)
- diary-service가 chat-service에 강결합 (sentence feedback / validation / diarychat AI) → chat-service 응답 지연이 일기 작성 UX에 직접 영향
- Redis ZSET 휘발 시 랭킹 재구축 배치 필수 (운영 부담)

### 후속 결정이 필요한 항목
- shorts/feedback 재배치 시점
- learning-service 실제 활성화 시점
- chat-service AI gateway: unary vs streaming RPC (Phase B-3 작성 시 결정)
- 회원 탈퇴 Saga 보상 정책 (자동 롤백 vs 수동 알림)
- Python LLM 서버의 contracts/proto 공유 방식 (Java/Python 양쪽 빌드)
- MySQL 스키마별 별도 DB user 운영 여부
- ArchUnit 모듈 의존 규칙 정의 시점
- AI 호출 캐싱·rate limiting 정책 (chat-service 책임)
- **활동별 점수 가중치 정책** (`DIARY_CREATED`, `COMMENT_CREATED`, `SENTENCE_FEEDBACK_ACCEPTED`, `VOICE_INPUT_PROCESSED` 등 — platform-service)
- **주간/월간/일간 랭킹 키 정책** (예: `ranking:weekly:{yyyyww}` + 주 단위 만료)
- **Redis ZSET 재구축 배치 정책** (Kafka retention 안에서 재처리 vs 각 서비스 SoT 재계산)
- **랭킹 조회 시 사용자 표시명 처리 방식**: 매번 identity-service gRPC 호출 vs Redis 캐시 Read Model

## 참고
- 관련 ADR: ADR-0001 (인증 — auth/user/profile 같은 스키마)
- PRD: `docs/prd/{auth,user,profile,diary,comment,validation,diarychat,chat,sentence,word,shorts,event,feedback}/`
- PRD (신규 — sentence feedback): `docs/prd/diary/{requestSentenceFeedback,acceptSentenceFeedback,rejectSentenceFeedback}.md`
- 외부: Eric Evans *Domain-Driven Design* (Bounded Context), Chris Richardson *Microservices Patterns* (Saga, Outbox), Vaughn Vernon *Implementing Domain-Driven Design*
