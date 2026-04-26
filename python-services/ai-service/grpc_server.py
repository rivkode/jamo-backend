"""gRPC AiService 서버 — chat-service (Java) 만 호출 (ADR-0003).

실제 AiService 구현은 contracts/proto/ai.proto 빌드 산출물
(proto/ai_pb2.py, proto/ai_pb2_grpc.py) 이 생성된 후 추가.
첫 골격 단계는 placeholder 만.

향후 추가:
- ai/llm/client.py     OpenAI / vLLM 추상
- ai/stt/whisper.py    Whisper / OpenAI Whisper API
- ai/tts/openai_tts.py OpenAI TTS / 자체

빌드:
- contracts/src/main/proto/ai.proto 작성 후
- uv run python -m grpc_tools.protoc \\
      --proto_path=../../contracts/src/main/proto \\
      --python_out=proto --grpc_python_out=proto \\
      ../../contracts/src/main/proto/ai.proto
- 빌드 자동화 (Makefile / Gradle task) — ADR-0004 §7
"""
import asyncio
import logging
import signal

logger = logging.getLogger(__name__)


async def serve(port: int = 9090) -> None:
    """gRPC 서버 시작.

    실제 AiServiceServicer 등록은 contracts/proto/ai.proto 빌드 후 추가.
    """
    logger.info("ai-service gRPC server placeholder (port=%d)", port)
    logger.info("ai.proto 빌드 후 AiServiceServicer 등록 필요 (ADR-0003 / ADR-0004)")

    # 첫 단계 placeholder: 종료 신호 대기만
    stop_event = asyncio.Event()
    loop = asyncio.get_event_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, stop_event.set)

    try:
        await stop_event.wait()
    finally:
        logger.info("ai-service gRPC server placeholder shutting down")


if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s — %(message)s",
    )
    asyncio.run(serve())
