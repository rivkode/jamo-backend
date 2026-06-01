# ai-service 로컬 개발 — proto 생성 / 테스트 / 서버. uv 필요 (https://docs.astral.sh/uv).
PROTO_SRC := ../../contracts/src/main/proto

.PHONY: proto test run lint

# ai.proto → proto/ai_pb2.py, proto/ai_pb2_grpc.py (+ 패키지 임포트 보정)
proto:
	uv run python -m grpc_tools.protoc \
		--proto_path=$(PROTO_SRC) \
		--python_out=proto --grpc_python_out=proto \
		$(PROTO_SRC)/ai.proto
	sed -i.bak 's/^import ai_pb2/from proto import ai_pb2/' proto/ai_pb2_grpc.py && rm -f proto/ai_pb2_grpc.py.bak

test: proto
	uv run pytest -q

run: proto
	uv run python grpc_server.py

lint:
	uv run ruff check .
