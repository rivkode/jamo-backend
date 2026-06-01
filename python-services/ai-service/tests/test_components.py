"""ai/* 컴포넌트 단위 테스트 — OpenAI 클라이언트 mock (proto 불요, 어디서나 실행)."""
from unittest.mock import MagicMock

from ai.llm.completer import Completer
from ai.stt.transcriber import Transcriber
from ai.tts.synthesizer import Synthesizer


class TestTranscriber:
    def test_returns_text_and_passes_model_file_language(self):
        client = MagicMock()
        client.audio.transcriptions.create.return_value = MagicMock(text="안녕하세요")

        result = Transcriber(client, model="gpt-4o-mini-transcribe").transcribe(b"\x00\x01", "wav", "ko")

        assert result.text == "안녕하세요"
        assert result.language == "ko"
        assert result.confidence == 0.0
        _, kwargs = client.audio.transcriptions.create.call_args
        assert kwargs["model"] == "gpt-4o-mini-transcribe"
        assert kwargs["file"][0] == "audio.wav"
        assert kwargs["file"][1] == b"\x00\x01"
        assert kwargs["language"] == "ko"

    def test_no_language_omits_kwarg_and_uses_format_extension(self):
        client = MagicMock()
        client.audio.transcriptions.create.return_value = MagicMock(text="hi")

        Transcriber(client).transcribe(b"x", "mp3", None)

        _, kwargs = client.audio.transcriptions.create.call_args
        assert "language" not in kwargs
        assert kwargs["file"][0] == "audio.mp3"


class TestSynthesizer:
    def test_returns_mp3_audio_with_defaults(self):
        client = MagicMock()
        client.audio.speech.create.return_value = MagicMock(content=b"AUDIO")

        result = Synthesizer(client).synthesize("hello", "", 0.0, None)

        assert result.audio == b"AUDIO"
        assert result.audio_format == "mp3"
        _, kwargs = client.audio.speech.create.call_args
        assert kwargs["voice"] == "alloy"   # 기본 voice
        assert kwargs["speed"] == 1.0        # 0 → 1.0
        assert kwargs["response_format"] == "mp3"

    def test_speed_clamped_and_voice_passthrough(self):
        client = MagicMock()
        client.audio.speech.create.return_value = MagicMock(content=b"a")

        Synthesizer(client).synthesize("t", "nova", 9.0, None)

        _, kwargs = client.audio.speech.create.call_args
        assert kwargs["speed"] == 4.0        # 9.0 → max 4.0
        assert kwargs["voice"] == "nova"


class TestCompleter:
    def test_maps_response_and_omits_zero_max_tokens(self):
        client = MagicMock()
        client.chat.completions.create.return_value = MagicMock(
            choices=[MagicMock(message=MagicMock(content="응답"), finish_reason="stop")],
            usage=MagicMock(prompt_tokens=5, completion_tokens=3),
            model="gpt-4o-mini",
        )

        result = Completer(client).complete("프롬프트", 0.7, 0, None)

        assert result.completion == "응답"
        assert result.prompt_tokens == 5
        assert result.completion_tokens == 3
        assert result.finish_reason == "stop"
        _, kwargs = client.chat.completions.create.call_args
        assert kwargs["temperature"] == 0.7
        assert "max_tokens" not in kwargs    # 0 → 생략 (모델 기본값)

    def test_max_tokens_and_model_override(self):
        client = MagicMock()
        client.chat.completions.create.return_value = MagicMock(
            choices=[MagicMock(message=MagicMock(content="x"), finish_reason="length")],
            usage=MagicMock(prompt_tokens=1, completion_tokens=1),
            model="gpt-4o",
        )

        Completer(client).complete("p", 0.5, 100, "gpt-4o")

        _, kwargs = client.chat.completions.create.call_args
        assert kwargs["model"] == "gpt-4o"
        assert kwargs["max_tokens"] == 100
