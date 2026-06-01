"""TTS — OpenAI tts-1 로 텍스트 → 음성."""
import logging
import os
from dataclasses import dataclass

logger = logging.getLogger(__name__)

DEFAULT_TTS_MODEL = "tts-1"
DEFAULT_VOICE = "alloy"
DEFAULT_FORMAT = "mp3"
MIN_SPEED = 0.25
MAX_SPEED = 4.0


@dataclass
class SynthesisResult:
    audio: bytes
    audio_format: str


class Synthesizer:
    """OpenAI audio.speech 래퍼. 모델/기본 voice 는 env(TTS_MODEL/TTS_VOICE) override 가능."""

    def __init__(self, client, model: str | None = None, default_voice: str | None = None) -> None:
        self._client = client
        self._model = model or os.environ.get("TTS_MODEL") or DEFAULT_TTS_MODEL
        self._default_voice = default_voice or os.environ.get("TTS_VOICE") or DEFAULT_VOICE

    def synthesize(self, text: str, voice: str, speed: float, language: str | None) -> SynthesisResult:
        resp = self._client.audio.speech.create(
            model=self._model,
            voice=voice or self._default_voice,
            input=text,
            speed=self._clamp_speed(speed),
            response_format=DEFAULT_FORMAT,
        )
        # openai>=1.x: HttpxBinaryResponseContent.content 가 bytes.
        audio = resp.content
        return SynthesisResult(audio=audio, audio_format=DEFAULT_FORMAT)

    @staticmethod
    def _clamp_speed(speed: float) -> float:
        if not speed or speed <= 0:
            return 1.0
        return max(MIN_SPEED, min(MAX_SPEED, speed))
