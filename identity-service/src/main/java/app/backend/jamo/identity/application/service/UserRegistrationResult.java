package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.domain.model.user.User;

import java.util.Objects;

/**
 * {@link UserRegistrationService#findOrRegister} 의 결과.
 *
 * <p>callback 응답에서 SPA 가 신규/기존 사용자 분기와 displayName truncate 안내를
 * 위해 사용 (ADR-0006 결정 3 / 4).
 */
public record UserRegistrationResult(
        User user,
        boolean isNewUser,
        boolean displayNameTruncated
) {
    public UserRegistrationResult {
        Objects.requireNonNull(user, "user");
    }
}
