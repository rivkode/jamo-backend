"""OpenAI 클라이언트 팩토리 — OPENAI_API_KEY 환경변수에서 생성.

ADR-0003: ai-service 만 OpenAI 를 직접 호출. chat-service 는 gRPC AiService 경유.
"""
import os

from openai import OpenAI

DEFAULT_TIMEOUT_SECONDS = 60.0


def build_openai_client() -> OpenAI:
    """환경변수 OPENAI_API_KEY 로 OpenAI 클라이언트 생성. 미설정 시 기동 실패(fail-fast).

    timeout 명시 — OpenAI 무응답 시 gRPC 워커 무한 점유(고갈) 방어 (security H2). env OPENAI_TIMEOUT override.
    """
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY environment variable is required")
    base_url = os.environ.get("OPENAI_BASE_URL") or None
    timeout = float(os.environ.get("OPENAI_TIMEOUT", DEFAULT_TIMEOUT_SECONDS))
    return OpenAI(api_key=api_key, base_url=base_url, timeout=timeout)
