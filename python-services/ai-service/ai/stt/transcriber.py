"""STT — OpenAI gpt-4o-mini-transcribe 로 음성 → 텍스트."""
import logging
import os
from dataclasses import dataclass

logger = logging.getLogger(__name__)

DEFAULT_STT_MODEL = "gpt-4o-mini-transcribe"


@dataclass
class TranscriptResult:
    text: str
    language: str
    confidence: float


class Transcriber:
    """OpenAI audio.transcriptions 래퍼. 모델은 env STT_MODEL 로 override 가능."""

    def __init__(self, client, model: str | None = None) -> None:
        self._client = client
        self._model = model or os.environ.get("STT_MODEL") or DEFAULT_STT_MODEL

    def transcribe(self, audio: bytes, audio_format: str, language: str | None) -> TranscriptResult:
        # OpenAI 는 (filename, bytes) 튜플로 파일 업로드 — 확장자가 포맷 힌트.
        filename = f"audio.{audio_format or 'wav'}"
        kwargs: dict = {"model": self._model, "file": (filename, audio)}
        if language:
            kwargs["language"] = language

        resp = self._client.audio.transcriptions.create(**kwargs)
        text = getattr(resp, "text", "") or ""
        # gpt-4o-mini-transcribe 는 confidence/감지언어를 별도 제공하지 않음 — 입력 echo + 0.
        return TranscriptResult(text=text, language=language or "", confidence=0.0)
