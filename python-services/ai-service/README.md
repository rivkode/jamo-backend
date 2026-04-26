# ai-service (Python)

jamo 의 **AI 추론 게이트웨이**. LLM + STT + TTS 모든 AI 모델 호출을 흡수 ([ADR-0003](../../docs/adr/0003-ai-call-architecture.md)).

## 책임

| | |
|---|---|
| **호출자** | chat-service (Java) **만** — gRPC `AiService` |
| **호출 대상** | OpenAI API / vLLM / Whisper / TTS 모델 / 자체 모델 |
| **상태** | 무상태 (또는 단기 캐시) |
| **노출** | gRPC 9090 (chat-service) + FastAPI 8086 (health/admin only) |

> 사용자 / 외부 / 다른 Java 서비스 직접 호출 **금지** (ArchUnit R8 으로 차단 — `.claude/skills/module-boundary/references/archunit-rules.md`).

## 디렉토리

```
ai-service/
├── main.py                # FastAPI (health/admin)
├── grpc_server.py         # gRPC AiService 서버 (placeholder)
├── ai/
│   ├── llm/               # OpenAI / vLLM 추상
│   ├── stt/               # Whisper / OpenAI Whisper API
│   └── tts/               # OpenAI TTS / 자체
├── proto/                 # contracts/*.proto 에서 생성된 .py (빌드시 자동)
├── pyproject.toml         # uv 패키지 매니저
├── .gitignore
└── README.md              # 본 파일
```

## 빌드 / 실행

### 1. 환경 준비 (uv)

```bash
# uv 설치 (없으면)
curl -LsSf https://astral.sh/uv/install.sh | sh

# 의존성 동기화
cd python-services/ai-service
uv sync
```

### 2. proto 생성 (contracts 변경 시 매번)

```bash
# contracts/src/main/proto/*.proto → Python *_pb2.py 생성
uv run python -m grpc_tools.protoc \
    --proto_path=../../contracts/src/main/proto \
    --python_out=proto \
    --grpc_python_out=proto \
    ../../contracts/src/main/proto/ai.proto
```

> 빌드 자동화 방식 (Makefile / Gradle task / pre-commit) 은 [ADR-0004](../../docs/adr/0004-contracts-naming-and-versioning.md) §7 권고 = Makefile.

### 3. 실행

```bash
# REST (health/admin)
uv run python main.py        # → http://localhost:8086/health

# gRPC (chat-service 호출 수신, 첫 단계는 placeholder)
uv run python grpc_server.py # → :9090
```

### 4. 테스트 / 린트

```bash
uv run pytest
uv run ruff check
uv run mypy .
```

## 다음 단계

1. **`contracts/src/main/proto/ai.proto` 작성** (ADR-0005 결정 후) — `AiService.Complete / SpeechToText / TextToSpeech` 메서드 정의
2. **proto 빌드 자동화** (Makefile)
3. **`AiServiceServicer` 구현** — `grpc_server.py` 에 등록
4. **OpenAI 클라이언트 추상화** (`ai/llm/openai_client.py`)
5. **컨테이너 빌드** (Dockerfile)
6. **chat-service 와 통합 테스트**

자세한 결정 사항은 [ADR-0003 후속 결정 항목](../../docs/adr/0003-ai-call-architecture.md#후속-결정이-필요한-항목) 참조.

## 관련 문서

- [ADR-0003 AI 호출 분리](../../docs/adr/0003-ai-call-architecture.md)
- [ADR-0004 contracts 명명/버전 표준](../../docs/adr/0004-contracts-naming-and-versioning.md)
- [.claude/skills/module-boundary §5.3 Java↔Python gRPC](../../.claude/skills/module-boundary/SKILL.md)
