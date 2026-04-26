package app.backend.jamo.common.auth;

@FunctionalInterface
public interface BlacklistChecker {

    boolean isBlacklisted(String sessionId);

    static BlacklistChecker noop() {
        return sessionId -> false;
    }
}
