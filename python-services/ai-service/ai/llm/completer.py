"""LLM — OpenAI chat completions (Complete RPC). S4 AI 자동응답의 기반."""
import logging
import os
from dataclasses import dataclass

logger = logging.getLogger(__name__)

DEFAULT_LLM_MODEL = "gpt-4o-mini"


@dataclass
class CompletionResult:
    completion: str
    prompt_tokens: int
    completion_tokens: int
    finish_reason: str
    model: str


class Completer:
    """OpenAI chat.completions 래퍼. 프롬프트는 chat-service 가 템플릿 적용 완료 후 전달 (ADR-0003)."""

    def __init__(self, client, model: str | None = None) -> None:
        self._client = client
        self._model = model or os.environ.get("LLM_MODEL") or DEFAULT_LLM_MODEL

    def complete(self, prompt: str, temperature: float, max_tokens: int, model: str | None) -> CompletionResult:
        kwargs: dict = {
            "model": model or self._model,
            "messages": [{"role": "user", "content": prompt}],
            "temperature": temperature,
        }
        if max_tokens and max_tokens > 0:
            kwargs["max_tokens"] = max_tokens

        resp = self._client.chat.completions.create(**kwargs)
        choice = resp.choices[0]
        usage = resp.usage
        return CompletionResult(
            completion=choice.message.content or "",
            prompt_tokens=getattr(usage, "prompt_tokens", 0) if usage else 0,
            completion_tokens=getattr(usage, "completion_tokens", 0) if usage else 0,
            finish_reason=choice.finish_reason or "stop",
            model=resp.model,
        )
