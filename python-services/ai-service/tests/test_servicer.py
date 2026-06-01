"""AiServiceServicer 매핑 테스트 — proto 생성 필요 (Docker 빌드 후 / make proto 후 실행).

proto 미생성 환경에서는 모듈 전체 skip.
"""
from unittest.mock import MagicMock

import pytest

pytest.importorskip("grpc")
try:
    from grpc_server import AiServiceServicer
    from proto import ai_pb2
except Exception:  # noqa: BLE001
    pytest.skip("proto not generated (run make proto / Docker build)", allow_module_level=True)

from ai.llm.completer import CompletionResult
from ai.stt.transcriber import TranscriptResult
from ai.tts.synthesizer import SynthesisResult


def _servicer():
    return AiServiceServicer(MagicMock(), MagicMock(), MagicMock())


def test_speech_to_text_maps_request_and_echoes_request_id():
    servicer = _servicer()
    servicer._transcriber.transcribe.return_value = TranscriptResult("hi", "ko", 0.0)

    req = ai_pb2.SpeechToTextRequest(audio=b"xyz", format="wav", language="ko", request_id="r1")
    resp = servicer.SpeechToText(req, MagicMock())

    assert resp.text == "hi"
    assert resp.language == "ko"
    assert resp.request_id == "r1"
    servicer._transcriber.transcribe.assert_called_once_with(b"xyz", "wav", "ko")


def test_speech_to_text_generates_request_id_when_absent():
    servicer = _servicer()
    servicer._transcriber.transcribe.return_value = TranscriptResult("t", "", 0.0)
    resp = servicer.SpeechToText(ai_pb2.SpeechToTextRequest(audio=b"x", format="mp3"), MagicMock())
    assert resp.request_id  # 비어있지 않은 UUID 생성


def test_text_to_speech_maps():
    servicer = _servicer()
    servicer._synthesizer.synthesize.return_value = SynthesisResult(b"AUDIO", "mp3")

    req = ai_pb2.TextToSpeechRequest(text="hello", voice="nova", speed=1.5, request_id="r2")
    resp = servicer.TextToSpeech(req, MagicMock())

    assert resp.audio == b"AUDIO"
    assert resp.format == "mp3"
    assert resp.request_id == "r2"
    servicer._synthesizer.synthesize.assert_called_once_with("hello", "nova", 1.5, None)


def test_complete_maps():
    servicer = _servicer()
    servicer._completer.complete.return_value = CompletionResult("응답", 5, 3, "stop", "gpt-4o-mini")

    req = ai_pb2.CompleteRequest(prompt="p", temperature=0.7, max_tokens=0, request_id="r3")
    resp = servicer.Complete(req, MagicMock())

    assert resp.completion == "응답"
    assert resp.prompt_tokens == 5
    assert resp.finish_reason == "stop"
    assert resp.model == "gpt-4o-mini"
    assert resp.request_id == "r3"


def test_stt_failure_aborts_internal():
    servicer = _servicer()
    servicer._transcriber.transcribe.side_effect = RuntimeError("openai down")
    context = MagicMock()

    servicer.SpeechToText(ai_pb2.SpeechToTextRequest(audio=b"x", format="wav"), context)

    context.abort.assert_called_once()
    args, _ = context.abort.call_args
    import grpc
    assert args[0] == grpc.StatusCode.INTERNAL
