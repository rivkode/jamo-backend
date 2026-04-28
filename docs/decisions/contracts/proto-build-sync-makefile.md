# Decision: contracts proto Java↔Python 빌드 동기화 — Makefile 채택

- **상태**: Accepted
- **결정일**: 2026-04-28
- **결정자**: jonghun
- **PR**: `feature/contracts-ai-proto`
- **관련 ADR**: [ADR-0003 AI 호출 아키텍처](../../adr/0003-ai-call-architecture.md), [ADR-0004 contracts 명명·버전](../../adr/0004-contracts-naming-and-versioning.md)
- **관련 카탈로그**: [`contracts-catalog.md`](../../architecture/contracts-catalog.md) §1.4 결정 대기

## 컨텍스트

`:contracts` 모듈의 `*.proto` 변경 시 두 빌드 산출물을 모두 갱신해야 한다 ([ADR-0003 §후속결정 "proto 빌드 자동화"](../../adr/0003-ai-call-architecture.md), [ADR-0004 §7 빌드 동기화](../../adr/0004-contracts-naming-and-versioning.md)).

| 측 | 도구 | 산출물 |
|---|---|---|
| Java | `protobuf-gradle-plugin` (`generateProto`) | `contracts/build/generated/source/proto/main/java/` |
| Python (ai-service) | `python -m grpc_tools.protoc` | `python-services/ai-service/proto/*_pb2.py` / `*_pb2_grpc.py` |

ADR-0004 §7 은 자동화 후보 4개를 제시:
- (a) Gradle task 가 Python `grpc_tools.protoc` trigger
- (b) `Makefile` 의 `make proto` ⭐ 권고 (첫 단계)
- (c) pre-commit hook
- (d) CI step (PR 빌드 시 양쪽 검증)

본 문서는 ai.proto 첫 PR 시점에서 (b) 채택을 박제한다.

## 결정 — Option (b) Makefile

루트에 `Makefile` 을 두고 `make proto` 가 Java + Python 양쪽 stub 을 생성한다.

```makefile
PROTO_DIR := contracts/src/main/proto
PYTHON_OUT := python-services/ai-service/proto

.PHONY: proto proto-java proto-python

proto: proto-java proto-python

proto-java:
	./gradlew :contracts:generateProto

proto-python:
	cd python-services/ai-service && \
	uv run python -m grpc_tools.protoc \
		--proto_path=../../$(PROTO_DIR) \
		--python_out=proto \
		--grpc_python_out=proto \
		../../$(PROTO_DIR)/ai.proto
```

> **현 시점 범위**: ai-service 가 호출자/제공자로 등장하는 proto 는 `ai.proto` 만이라, `proto-python` 은 `ai.proto` 만 생성한다. 향후 다른 proto 가 Python 측 빌드 입력이 되면 동일 라인에 추가.

## 검토한 옵션

### Option (b) Makefile — 채택
**장점**:
- 가장 단순. Java (`./gradlew`) + Python (`uv run`) 의 양쪽 도구를 그대로 호출.
- macOS / Linux 표준 도구 (`make`) 만 사용 — 추가 의존 0건.
- 개발자가 한 줄 (`make proto`) 로 양쪽 동기화. 출력 명확.
- `.PHONY` 로 캐시 문제 없음 (gradle / uv 가 각자 incremental 처리).

**단점**:
- 수동 실행 필요 — proto 변경 후 깜빡 잊으면 stub 미갱신.
- Windows 개발자에 친화적이지 않음 (1인 개발 + macOS/Linux 사용 가정으로 수용).
- CI 검증 부재 — 후속 (d) 도입 시 보완.

### Option (a) Gradle task — 보류
- Java 쪽에서 Python 빌드를 trigger 하는 발상은 멀티언어 monorepo 에서 흔하지만, Gradle 안에서 `uv run python -m grpc_tools.protoc` 를 호출하는 코드를 유지하는 비용이 발생.
- Python 의존성 (`grpcio-tools`) 이 설치 안 된 환경에서 Java 빌드가 실패하는 false-fail 위험.
- 학습 가치는 있으나 첫 단계 over-engineering.

### Option (c) pre-commit hook — 보류
- 개발자 머신에 hook 설치 강제. 1인 개발이라 큰 비용 아니지만, hook 실패가 `git commit` 흐름을 막아 hot-fix 시 마찰.
- proto 변경이 빈번하지 않아 ROI 낮음.

### Option (d) CI step — 후속 도입 검토
- PR 빌드 시 `make proto` 실행 후 git diff 가 비어있는지 검사하면 stub drift 자동 탐지 가능.
- 현 시점은 GitHub Actions / CI 파이프라인 자체가 미정 — Phase 1+ 에서 (d) 채택을 별도 ADR 로 박제.

### 왜 (b) 가 우선인가
- ADR-0004 §7 권고 일치.
- 추가 인프라/도구 학습 0.
- 이후 (a)/(c)/(d) 가 모두 (b) 의 명령 (`./gradlew :contracts:generateProto`, `uv run ... protoc`) 위에 얹히는 구조 — 어떤 후속을 채택해도 본 결정이 잠재 cost 가 되지 않음.

## 결과 및 영향

### 즉시
- 루트 `Makefile` 추가.
- 개발자는 proto 수정 시 `make proto` 1회 실행 (또는 `make proto-java` / `make proto-python` 분리 실행).
- 본 PR 에서 검증 범위: **Java 측 만** (`./gradlew :contracts:build` 성공, `AiServiceGrpc` + 6 message 생성 확인).
- Python 측 (`make proto-python`) 은 본 PR 시점 로컬에 `uv` 미설치라 **검증 건너뜀**. `make proto-python` 명령은 `python-services/ai-service/README.md` §2 의 동일 명령을 옮긴 것 — ai-service 구현 PR (chat 도메인 진입 후) 에서 실 검증.

### Python 측 산출물 git 추적
- `python-services/ai-service/.gitignore` 의 기존 `proto/*_pb2.py` / `*_pb2_grpc.py` / `*_pb2.pyi` 규칙 유지 (ADR-0004 §7 권고).
- Java 측 산출물 (`contracts/build/...`) 은 Gradle 표준 `build/` 무시.

### 운영 / CI
- 본 결정은 **개발자 로컬** 표준만 다룸. CI 자동화는 (d) 를 별도 ADR 로 박제할 때까지 미적용.
- proto stub drift (proto 수정 후 Python stub 미갱신) 위험이 남음 — **후속 별도 PR 후보**: `(d) CI step` 채택 ADR.

### Non-Goals
- buf lint 등 자동 검증 도구 도입 (ADR-0004 §결과 영향 — Phase 1+ 검토).
- gRPC schema registry / Confluent SR.
- Windows 개발자 호환성.

## 후속 별도 PR 후보

- CI step (Option d) 도입 — GitHub Actions 첫 워크플로 정의 시점에 함께. proto 수정 PR 에 자동 검증.
- proto 변경 시 카탈로그 (`contracts-catalog.md`) 갱신 강제 검사 — diff hook 또는 ArchUnit-like 도구.

## 참고

- [ADR-0003 §후속결정 — proto 빌드 자동화](../../adr/0003-ai-call-architecture.md)
- [ADR-0004 §7 — Java + Python 빌드 동기화](../../adr/0004-contracts-naming-and-versioning.md)
- [`contracts-catalog.md`](../../architecture/contracts-catalog.md) §1.2
