# ai-service (Python) — gRPC AiService (LLM/STT/TTS). ADR-0003: chat-service 만 호출.
# build context = repo root (contracts/ai.proto 접근). docker-compose: dockerfile 경로 지정.
FROM ghcr.io/astral-sh/uv:python3.12-bookworm-slim

WORKDIR /app
ENV TZ=Asia/Seoul \
    UV_LINK_MODE=copy \
    UV_COMPILE_BYTECODE=1 \
    PYTHONPATH=/app

# 1) 의존성 (pyproject 만 먼저 — 레이어 캐시)
COPY python-services/ai-service/pyproject.toml /app/pyproject.toml
RUN uv sync

# 2) 소스 + proto 정의 복사
COPY python-services/ai-service/ /app/
COPY contracts/src/main/proto/ai.proto /app/contracts-proto/ai.proto

# 3) proto → Python 생성 (ai_pb2, ai_pb2_grpc) + 패키지 임포트 보정
#    grpc 생성물의 'import ai_pb2' 를 'from proto import ai_pb2' 로 (proto 패키지 임포트 정합).
RUN uv run python -m grpc_tools.protoc \
        --proto_path=/app/contracts-proto \
        --python_out=/app/proto --grpc_python_out=/app/proto \
        /app/contracts-proto/ai.proto \
 && sed -i 's/^import ai_pb2/from proto import ai_pb2/' /app/proto/ai_pb2_grpc.py

# 4) non-root
RUN useradd --system --uid 1001 ai && chown -R ai:ai /app
USER ai

EXPOSE 9090
# uv 캐시(비root 권한) 의존 회피 — 빌드에서 만든 .venv 의 python 을 직접 실행.
CMD ["/app/.venv/bin/python", "grpc_server.py"]
