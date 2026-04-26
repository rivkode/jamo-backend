"""contracts/src/main/proto/*.proto 에서 생성된 Python 빌드 산출물 디렉토리.

빌드 명령:
  uv run python -m grpc_tools.protoc \\
      --proto_path=../../contracts/src/main/proto \\
      --python_out=. --grpc_python_out=. \\
      ../../contracts/src/main/proto/ai.proto

산출물 (proto 작성 후):
- ai_pb2.py
- ai_pb2_grpc.py

자세한 빌드 자동화는 ADR-0004 §7 참조.
"""
