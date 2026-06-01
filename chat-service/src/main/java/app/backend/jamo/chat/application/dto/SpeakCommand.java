package app.backend.jamo.chat.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * TTS Command. @param text 합성할 텍스트, userId 응답 echo.
 */
public record SpeakCommand(UUID userId, String text) {

    public SpeakCommand {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(text, "text");
    }
}
