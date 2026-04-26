"""FastAPI 진입점 — REST endpoints (health, admin).

ai-service 의 핵심 책임은 gRPC AiService (grpc_server.py 참조).
FastAPI 는 health check / admin / 디버그 endpoint 만 노출.
사용자 / 다른 Java 서비스가 직접 호출하지 않음 (ADR-0003).
"""
from fastapi import FastAPI

app = FastAPI(title="jamo ai-service", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/")
def root() -> dict[str, str]:
    return {
        "service": "ai-service",
        "language": "Python 3.12",
        "framework": "FastAPI + grpcio",
        "responsibility": "LLM/STT/TTS 추론 gateway (ADR-0003)",
        "note": "사용자/외부 직접 호출 금지. chat-service 가 gRPC AiService 만 호출.",
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8086)
