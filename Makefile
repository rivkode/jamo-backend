PROTO_DIR := contracts/src/main/proto
PYTHON_OUT := python-services/ai-service/proto

# ai-service (Python) 의 빌드 입력 proto 목록. 새 Python 호출자/제공자가 등장하면
# 본 변수에만 추가 — proto-python 타겟 자체는 수정 불필요.
PYTHON_PROTOS := ai.proto

.PHONY: proto proto-java proto-python proto-clean help

help:
	@echo "Available targets:"
	@echo "  make proto         - Java + Python proto stubs 모두 생성"
	@echo "  make proto-java    - Java 측 protobuf 생성 (./gradlew :contracts:generateProto)"
	@echo "  make proto-python  - Python 측 grpcio-tools 로 ai-service 의 stub 생성"
	@echo "  make proto-clean   - Python 생성 산출물 삭제 (Java 측은 ./gradlew clean)"
	@echo ""
	@echo "참고: docs/decisions/contracts/proto-build-sync-makefile.md (ADR-0004 §7)"

proto: proto-java proto-python

proto-java:
	./gradlew :contracts:generateProto

# ai-service (Python) 측 stub. 입력 목록은 PYTHON_PROTOS 변수에서 관리.
# 현재 chat.proto / identity.proto 는 Java 전용이므로 ai.proto 만 포함.
proto-python:
	cd python-services/ai-service && \
	uv run python -m grpc_tools.protoc \
		--proto_path=../../$(PROTO_DIR) \
		--python_out=proto \
		--grpc_python_out=proto \
		$(addprefix ../../$(PROTO_DIR)/,$(PYTHON_PROTOS))

# __init__.py / .gitignore / 기타 수동 유지 파일은 보존. *_pb2*.py / *_pb2.pyi 만 삭제.
proto-clean:
	rm -f $(PYTHON_OUT)/*_pb2.py $(PYTHON_OUT)/*_pb2_grpc.py $(PYTHON_OUT)/*_pb2.pyi
