"""gRPC AiService 서버 — chat-service (Java) 만 호출 (ADR-0003).

AiService 3 메서드 구현:
- SpeechToText  : OpenAI gpt-4o-mini-transcribe (ai/stt/transcriber.py)
- TextToSpeech  : OpenAI tts-1 (ai/tts/synthesizer.py)
- Complete      : OpenAI chat completions (ai/llm/completer.py) — S4 기반

proto 빌드 산출물(proto/ai_pb2.py, proto/ai_pb2_grpc.py)은 Docker 빌드 시 grpc_tools.protoc 로 생성
(로컬은 Makefile `make proto`). ADR-0004 §7.
"""
import logging
import os
import signal
import threading
import uuid
from concurrent import futures

import grpc

from ai.llm.completer import Completer
from ai.openai_factory import build_openai_client
from ai.stt.transcriber import Transcriber
from ai.tts.synthesizer import Synthesizer
from proto import ai_pb2, ai_pb2_grpc

logger = logging.getLogger(__name__)

DEFAULT_PORT = 9090
MAX_WORKERS = 10
# STT audio 수신 상한 4MB (proto 가정), TTS audio 송신 여유 16MB — 메모리 DoS 방어 (security H1).
MAX_RECV_BYTES = 4 * 1024 * 1024
MAX_SEND_BYTES = 16 * 1024 * 1024


class AiServiceServicer(ai_pb2_grpc.AiServiceServicer):
    """proto AiService ↔ ai/* 컴포넌트 매핑. 컴포넌트 주입으로 테스트 가능."""

    def __init__(self, transcriber: Transcriber, synthesizer: Synthesizer, completer: Completer) -> None:
        self._transcriber = transcriber
        self._synthesizer = synthesizer
        self._completer = completer

    def SpeechToText(self, request, context):  # noqa: N802 (proto 규약)
        request_id = request.request_id or str(uuid.uuid4())
        try:
            result = self._transcriber.transcribe(
                request.audio, request.format, request.language or None
            )
            return ai_pb2.SpeechToTextResponse(
                text=result.text,
                confidence=result.confidence,
                language=result.language,
                request_id=request_id,
            )
        except Exception:  # noqa: BLE001
            logger.exception("SpeechToText failed request_id=%s", request_id)
            context.abort(grpc.StatusCode.INTERNAL, "speech-to-text failed")

    def TextToSpeech(self, request, context):  # noqa: N802
        request_id = request.request_id or str(uuid.uuid4())
        try:
            result = self._synthesizer.synthesize(
                request.text, request.voice, request.speed, request.language or None
            )
            return ai_pb2.TextToSpeechResponse(
                audio=result.audio,
                format=result.audio_format,
                request_id=request_id,
            )
        except Exception:  # noqa: BLE001
            logger.exception("TextToSpeech failed request_id=%s", request_id)
            context.abort(grpc.StatusCode.INTERNAL, "text-to-speech failed")

    def Complete(self, request, context):  # noqa: N802
        request_id = request.request_id or str(uuid.uuid4())
        try:
            result = self._completer.complete(
                request.prompt, request.temperature, request.max_tokens, request.model or None
            )
            return ai_pb2.CompleteResponse(
                completion=result.completion,
                prompt_tokens=result.prompt_tokens,
                completion_tokens=result.completion_tokens,
                finish_reason=result.finish_reason,
                model=result.model,
                request_id=request_id,
            )
        except Exception:  # noqa: BLE001
            logger.exception("Complete failed request_id=%s", request_id)
            context.abort(grpc.StatusCode.INTERNAL, "complete failed")


def build_servicer() -> AiServiceServicer:
    client = build_openai_client()
    return AiServiceServicer(Transcriber(client), Synthesizer(client), Completer(client))


def serve(port: int | None = None) -> None:
    resolved_port = port or int(os.environ.get("AI_GRPC_PORT", DEFAULT_PORT))
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=MAX_WORKERS),
        options=[
            ("grpc.max_receive_message_length", MAX_RECV_BYTES),
            ("grpc.max_send_message_length", MAX_SEND_BYTES),
        ],
    )
    ai_pb2_grpc.add_AiServiceServicer_to_server(build_servicer(), server)
    server.add_insecure_port(f"[::]:{resolved_port}")
    server.start()
    logger.info("ai-service gRPC server started on port %d (ADR-0003: chat-service only)", resolved_port)

    stop_event = threading.Event()
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, lambda *_: stop_event.set())
    try:
        stop_event.wait()
    finally:
        logger.info("ai-service gRPC server shutting down")
        server.stop(grace=5)


if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s — %(message)s",
    )
    serve()
