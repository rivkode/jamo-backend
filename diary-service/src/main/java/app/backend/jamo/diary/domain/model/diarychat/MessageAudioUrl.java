package app.backend.jamo.diary.domain.model.diarychat;

import app.backend.jamo.diary.domain.exception.InvalidChatMessageException;

import java.net.URI;

/**
 * 메시지에 첨부된 음성 녹음 URL VO — http/https + userInfo 차단 (avatar/audio URL 정합).
 *
 * <p>클라가 E.5 음성 업로드로 받은 audioUrl 을 첨부. optional (text-only 메시지는 null).
 */
public record MessageAudioUrl(String value) {

    public static final int MAX_LENGTH = 2048;

    public MessageAudioUrl {
        if (value == null || value.isBlank()) {
            throw new InvalidChatMessageException("audioUrl must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidChatMessageException("audioUrl too long");
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw new InvalidChatMessageException("audioUrl must start with http:// or https://");
        }
        try {
            URI uri = URI.create(value);
            if (uri.getUserInfo() != null) {
                throw new InvalidChatMessageException("audioUrl must not contain userInfo");
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidChatMessageException("audioUrl is not a valid URI");
        }
    }
}
