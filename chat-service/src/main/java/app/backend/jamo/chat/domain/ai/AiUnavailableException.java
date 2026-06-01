package app.backend.jamo.chat.domain.ai;

/**
 * ai-service 호출 실패 (gRPC 장애 / Circuit open / deadline) — presentation 에서 503.
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
