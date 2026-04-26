# ADR-0003: AI 호출 아키텍처 — chat-service(Java) ↔ ai-service(Python) 분리

- **상태**: Accepted
- **결정일**: 2026-04-26
- **결정자**: jonghun
- **관련 ADR**: [ADR-0002 서비스 분할](0002-service-decomposition.md) (chat-service 책임을 본 ADR 로 정정 + ai-service 신규 추가)

## 컨텍스트

ADR-0002 에서 "chat-service 가 AI 호출 단일 진입점" 으로 정했으나, 추가 명확화 결과 **AI 호출 흐름이 두 서비스로 분리되어야 함**이 식별되었다.

- **chat-service (Java/Spring)**: 프롬프트 템플릿 관리, 사용자 컨텍스트, 사용량/비용 추적, rate limit, fallback 정책, chat 도메인의 비즈니스 로직 (14 API). 다른 Java 서비스의 AI 진입점.
- **ai-service (Python)**: 순수 AI 추론 호출. **LLM(텍스트 생성) + STT(음성→텍스트) + TTS(텍스트→음성)** + 향후 비전 등 모든 AI 모델 호출을 흡수. 무상태. **Python AI 생태계(LangChain, vLLM, transformers, Whisper, TTS 모델 등)** 활용.

본 ADR 도입의 가장 큰 동기는 **gRPC 의 가치 검증** — 두 언어 서비스 간의 긴밀한 양방향 통신을 실제 흐름으로 등장시키는 것이다.

## 검토한 옵션

### Option A: chat-service Java 단독 (Python 분리 X)
- **장점**: 단순. 한 서비스만 운영
- **단점**: Python AI 생태계 활용 불가. LangChain·vLLM·Whisper 등 Java 호환성 낮음. 모델 관련 최신 도구 채택 어려움
- **적합성**: 비추 — Python 생태계 가치가 학습/실전 모두 큼

### Option B: chat-service(Java) + ai-service(Python), gRPC ⭐ 채택
- **장점**: 책임 분리 (비즈니스 vs 추론), Python 생태계 활용 자유, gRPC 도입 가치 검증, server-streaming 가능, 모델 교체가 ai-service 안에 격리, LLM/STT/TTS 모두 한 서비스에서 다룸
- **단점**: 서비스 1개 추가 (배포/운영), gRPC 인프라 필요(proto 빌드, Deadline, Circuit Breaker), 두 언어 디버깅 부담
- **적합성**: 학습 가치 + 사용자 의도 + Python 생태계 모두 부합

### Option C: chat-service 안에 Python embedded (GraalVM Python / Jython)
- **장점**: 단일 프로세스
- **단점**: GraalVM Python 안정성·성능 미검증, 일반 Python 라이브러리 호환성 불완전, 운영 사례 적음
- **적합성**: 비현실적

### Option D: chat-service ↔ ai-service HTTP REST (gRPC 대신)
- **장점**: 디버깅 편함 (curl/Postman), HTTP 인프라 친숙
- **단점**: gRPC 도입 의도와 충돌, server-streaming 어려움 (LLM 긴 응답 부적합), 양방향 streaming 없음, 타입 안전성 낮음, 음성 데이터 (STT/TTS) 의 바이너리 전송이 gRPC 가 더 자연스러움
- **적합성**: gRPC 학습 목적과 충돌

## 결정

**Option B 채택.** 이유:
1. **Python AI 생태계** (LangChain, vLLM, Whisper, TTS 모델 등) 활용이 AI 운영 학습의 핵심
2. **책임 경계 명확**: chat-service = 비즈니스/정책/사용량, ai-service = 순수 추론 (LLM/STT/TTS 통합)
3. **gRPC 의 학습 가치**: 두 언어 / 양방향 / streaming / 바이너리 전송 모두 등장
4. **모델·프롬프트 변경의 영향 격리**: chat-service 변경 없이 ai-service 만 교체 가능

### 세부 정책

| 항목 | chat-service (Java) | ai-service (Python) |
|---|---|---|
| **프레임워크** | Spring Boot 3.5 | FastAPI + grpcio |
| **언어/런타임** | Java 21 | Python 3.12+ |
| **MySQL 스키마** | `chat` | (없음 — stateless) |
| **상태** | 프롬프트 템플릿, 사용량 카운터, 사용자 컨텍스트, 채팅방/메시지 기록 | 무상태 (또는 단기 캐시) |
| **외부 호출** | ai-service (gRPC) — 다른 외부 X | OpenAI API / vLLM / Whisper / TTS 모델 / 자체 모델 |
| **gRPC 인터페이스 노출** | `AiAssistantService` (다른 Java 서비스가 호출) | `AiService` (chat-service 만 호출) |
| **사용자 직접 호출 (HTTP)** | chat 도메인 14 API | ❌ 없음 (chat-service 통과만) |
| **빌드 도구** | Gradle Kotlin DSL (멀티모듈) | uv 또는 poetry (별도) |
| **AI 모델 책임** | 모델 호출 X (위임만) | LLM(텍스트 생성), STT(음성→텍스트, Whisper류), TTS(텍스트→음성), 향후 비전 등 |

### 호출 흐름 (sentence-feedback 예시)

```
[SPA] ── HTTPS POST /diaries/sentence-feedback ──▶ [diary-service]
                                                         │
                                                         │ gRPC
                                                         │ AiAssistantService.requestSentenceFeedback
                                                         ▼
                                                   [chat-service (Java)]
                                                         │ - 프롬프트 템플릿 적용
                                                         │ - 사용자별 사용량 / rate limit 검증
                                                         │ - 사용자 컨텍스트 (이전 문장 등) 보강
                                                         │
                                                         │ gRPC
                                                         │ AiService.complete
                                                         ▼
                                                   [ai-service (Python)]
                                                         │ - prompt → completion 변환만
                                                         │ - 모델 라우팅 (OpenAI / vLLM / 자체)
                                                         │
                                                         │ HTTPS / API
                                                         ▼
                                                   [OpenAI / vLLM / 자체 모델]
```

### 호출 흐름 (음성 전사 — STT 예시, `chat.transcribeChat`)

```
[SPA] ── HTTPS POST /chat/transcribe (multipart audio) ──▶ [chat-service (Java)]
                                                                  │ - 사용량 / rate limit
                                                                  │ - 음성 파일 검증 (size/format)
                                                                  │
                                                                  │ gRPC
                                                                  │ AiService.speechToText
                                                                  │ (binary audio bytes)
                                                                  ▼
                                                            [ai-service (Python)]
                                                                  │ - Whisper 또는 외부 STT API 호출
                                                                  ▼
                                                            [Whisper / OpenAI Whisper API]
```

### Proto 분리

| Proto 파일 | 노출 측 | 호출 측 | 내용 |
|---|---|---|---|
| `chat.proto` (`AiAssistantService`) | chat-service (Java) | diary, learning, diarychat, validation 등 | 비즈니스 의미가 있는 메서드 (`requestSentenceFeedback`, `validateDiaryContent` 등) |
| `ai.proto` (`AiService`) | ai-service (Python) | chat-service (Java) | 일반화된 AI 메서드 — `complete`(LLM), `speechToText`(STT), `textToSpeech`(TTS), 향후 `completeStream`(server-streaming LLM) |

두 proto 모두 `:contracts` 모듈에 정의 → **Java 빌드** (`grpc-spring-boot-starter`) + **Python 빌드** (`grpcio-tools`) 양쪽 입력. proto 빌드 자동화는 Open Item.

### 모듈 위치

```
jamo-backend/                          # monorepo 루트
├── identity-service/                  # Java (Gradle 모듈)
├── diary-service/                     # Java (Gradle 모듈)
├── chat-service/                      # Java (Gradle 모듈, AiAssistantService 노출)
├── learning-service/                  # Java (Gradle 모듈, 비배포 시작)
├── platform-service/                  # Java (Gradle 모듈)
├── contracts/                         # proto + Kafka 이벤트 (Java/Python 양쪽 빌드 입력)
├── common-auth-jwt/                   # Java
├── common-infrastructure/             # Java
└── python-services/                   # Python 서비스 묶음 (Gradle 빌드와 분리)
    └── ai-service/
        ├── main.py                    # FastAPI app (REST: health/admin)
        ├── grpc_server.py             # gRPC AiService (LLM + STT + TTS)
        ├── ai/
        │   ├── llm/                   # OpenAI / vLLM 추상 클라이언트
        │   ├── stt/                   # Whisper / OpenAI Whisper API
        │   └── tts/                   # TTS 모델 (OpenAI TTS / 자체)
        ├── proto/                     # contracts/*.proto 에서 생성된 .py
        └── pyproject.toml             # uv 또는 poetry
```

### gRPC 운영 정책

| 호출 방향 | Deadline | Retry | Circuit Breaker | Fallback |
|---|---|---|---|---|
| 다른 서비스 → chat-service | 35초 (chat 자체 + ai 마진) | 0 (idempotent 보장 어려움) | ✅ | chat-service 가 정형 메시지 반환 |
| chat-service → ai-service (LLM) | 30초 | 1회 (네트워크 일시 장애만) | ✅ | chat-service 가 fallback 메시지 |
| chat-service → ai-service (STT) | 60초 (음성 길이 따라) | 1회 | ✅ | "음성 인식 실패" 메시지 |
| chat-service → ai-service (TTS) | 30초 | 1회 | ✅ | 텍스트만 반환 (음성 없이) |

- **응답**: 첫 단계는 **unary**. server-streaming 은 후속 ADR (UX 검증 후, 특히 LLM 긴 응답에서 효과 큼)
- **인증 전파**: 다른 서비스 → chat-service 는 Token Relay (사용자 access JWT 그대로 metadata). chat-service → ai-service 까지 사용자 JWT 전파할지는 Open Item (보안/추적성 trade-off)

## 결과 및 영향

### 긍정적
- chat-service Java 가 풍부한 비즈니스 로직 (Spring Aggregate / 도메인 모델) 보유
- ai-service Python 이 AI 생태계 (LangChain, vLLM, Whisper, TTS 등) 자유롭게 채택
- 모든 AI 모델 (LLM/STT/TTS/비전) 이 ai-service 한 곳에 집중되어 모델 추가/교체 시 chat-service 변경 없음
- gRPC 양방향 통신이 실제 흐름에 등장 — 학습 케이스 확보. 음성 바이너리 전송 (STT/TTS) 은 gRPC 가 특히 자연스러움
- Java/Python 멀티 언어 monorepo 운영 학습

### 부정적 / 트레이드오프
- 6번째 서비스 추가 = 1인 운영 부담 +1
- 두 언어 디버깅 (Java + Python) — IDE / 로그 / trace 모두 두 종류
- proto 빌드가 양쪽 (Java + Python) — 동기화 자동화 필요
- AI 호출 1회당 네트워크 hop 1개 추가 (다른 서비스 → chat → ai) — 응답시간 +수ms
- ai-service 장애 시 모든 AI 기능 (LLM/STT/TTS) 다운 — chat-service Circuit Breaker 로 fallback 필수
- ai-service 가 Python 이라 `module-boundary` 의 ArchUnit 등 Java 도구로 자동 검증 불가
- STT/TTS 가 함께 들어오면서 ai-service 의 책임 범위가 넓어짐 (LLM 만 분리하는 더 작은 변형도 가능했으나 통합 선택)

### 후속 결정이 필요한 항목

#### proto / 인터페이스
- [ ] `AiService.complete` 시그니처 (input: prompt + temperature + maxTokens + model 등, output: completion + usage + finishReason)
- [ ] `AiService.speechToText` 시그니처 (input: audio bytes + format + language, output: text + segments + confidence)
- [ ] `AiService.textToSpeech` 시그니처 (input: text + voice + speed, output: audio bytes + format)
- [ ] `AiAssistantService` 메서드 카탈로그 — 비즈니스 의미별(`requestSentenceFeedback`, `paraphrase`, `transcribe`...) vs 일반화된(`call(type, payload)` 단일 + type 필드)
- [ ] server-streaming RPC 도입 시점 (LLM 긴 응답 UX, STT 실시간 streaming)
- [ ] proto 빌드 자동화 (Gradle task ↔ Python `python -m grpc_tools.protoc` 동기화)
- [ ] 음성 바이너리 전송: gRPC unary message 크기 제한 (기본 4MB), 큰 파일은 client-streaming 으로 청크 전송

#### 보안 / 인증
- [ ] ai-service 까지 사용자 JWT 전파 여부 (보안 vs 디버깅 추적성)
- [ ] ai-service 의 service-to-service 인증 (mTLS? shared secret? 사내망 신뢰?)
- [ ] OpenAI / Whisper API key 등 비밀 관리 (ai-service 만 보유, chat-service 는 모름)
- [ ] 음성 파일의 PII 처리 (전사 후 원본 음성 보관 정책)

#### LLM / STT / TTS / 모델
- [ ] ai-service 가 호출하는 모델 카탈로그 (LLM: OpenAI gpt-4 / vLLM 자체 / 여러 모델 라우팅. STT: OpenAI Whisper API / 자체 Whisper. TTS: OpenAI TTS / ElevenLabs / 자체)
- [ ] prompt caching 정책 (Anthropic prompt cache, OpenAI prompt cache, 자체 캐시)
- [ ] 모델별 가격 추적 / 사용자별 quota
- [ ] 응답 sanitization (LLM 출력의 PII / 금칙어 / 코드 블록 처리)
- [ ] 컨텍스트 윈도우 초과 시 truncation 전략
- [ ] STT 결과의 화자 분리(diarization) 필요성
- [ ] TTS 음성 캐싱 (자주 쓰이는 문구)

#### 인프라 / 운영
- [ ] ai-service 컨테이너 빌드 / 배포 방식 (Docker, K8s)
- [ ] ai-service 모니터링 / 로깅 표준 (OpenTelemetry, structured logging)
- [ ] chat-service 의 사용량 카운터 / 비용 추적 정책
- [ ] ai-service 의 health check / readiness probe (모델 로딩 시간 고려)
- [ ] ai-service 수평 확장 정책 (stateless 라 단순. GPU 모델 로컬 호스팅 시 GPU 노드 친화도)

## 참고
- 관련 ADR: [ADR-0001 인증](0001-authentication-architecture.md), [ADR-0002 서비스 분할](0002-service-decomposition.md)
- 외부: [gRPC 공식](https://grpc.io/), [FastAPI](https://fastapi.tiangolo.com/), [grpcio Python](https://grpc.io/docs/languages/python/), LangChain, vLLM, Whisper
