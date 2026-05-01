package app.backend.jamo.identity.presentation.exception;

import app.backend.jamo.identity.domain.exception.AuthCodeExpiredException;
import app.backend.jamo.identity.domain.exception.AuthCodeNotFoundException;
import app.backend.jamo.identity.domain.exception.LoginInvalidException;
import app.backend.jamo.identity.domain.exception.LoginRateLimitedException;
import app.backend.jamo.identity.domain.exception.OAuthAuthenticationException;
import app.backend.jamo.identity.domain.exception.OAuthProviderCallFailedException;
import app.backend.jamo.identity.domain.exception.OAuthStateInvalidException;
import app.backend.jamo.identity.presentation.dto.AuthErrorCode;
import app.backend.jamo.identity.presentation.dto.AuthErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

import static org.assertj.core.api.Assertions.assertThat;

class AuthExceptionHandlerTest {

    private final AuthExceptionHandler handler = new AuthExceptionHandler();

    @Test
    void auth_code_not_found_maps_to_401_with_auth_code_invalid() {
        ResponseEntity<AuthErrorResponse> response =
                handler.handleAuthCodeInvalid(new AuthCodeNotFoundException("not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo(AuthErrorCode.AUTH_CODE_INVALID);
    }

    @Test
    void auth_code_expired_maps_to_401_with_auth_code_invalid() {
        ResponseEntity<AuthErrorResponse> response =
                handler.handleAuthCodeInvalid(new AuthCodeExpiredException("expired"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo(AuthErrorCode.AUTH_CODE_INVALID);
    }

    @Test
    void error_response_message_does_not_leak_domain_exception_message() {
        // SECRET-MARKER 는 도메인 예외 message 에 들어 있지만 클라이언트 응답에 노출되지 않아야 함
        AuthCodeNotFoundException ex = new AuthCodeNotFoundException(
                "auth code with SECRET-MARKER not found in Redis");

        ResponseEntity<AuthErrorResponse> response = handler.handleAuthCodeInvalid(ex);

        assertThat(response.getBody().message()).doesNotContain("SECRET-MARKER");
        assertThat(response.getBody().message()).doesNotContain("Redis");
    }

    @Test
    void login_invalid_maps_to_401_with_login_invalid_and_no_leak() {
        ResponseEntity<AuthErrorResponse> response =
                handler.handleLoginInvalid(new LoginInvalidException("SECRET account missing"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo(AuthErrorCode.LOGIN_INVALID);
        assertThat(response.getBody().message()).doesNotContain("SECRET");
        assertThat(response.getBody().message()).doesNotContain("missing");
    }

    @Test
    void login_rate_limited_maps_to_429_with_login_rate_limited_and_no_leak() {
        ResponseEntity<AuthErrorResponse> response =
                handler.handleLoginRateLimited(new LoginRateLimitedException("SECRET attempts=5"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().code()).isEqualTo(AuthErrorCode.LOGIN_RATE_LIMITED);
        assertThat(response.getBody().message()).doesNotContain("SECRET");
        assertThat(response.getBody().message()).doesNotContain("attempts");
    }

    @Test
    void illegal_argument_maps_to_400_with_validation_failed() {
        ResponseEntity<AuthErrorResponse> response =
                handler.handleIllegalArgument(new IllegalArgumentException("SECRET invalid email"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(AuthErrorCode.VALIDATION_FAILED);
        assertThat(response.getBody().message()).doesNotContain("SECRET");
    }

    @Test
    void http_message_not_readable_maps_to_400_with_validation_failed() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "malformed JSON",
                new MockHttpInputMessage(new byte[0]));

        ResponseEntity<AuthErrorResponse> response = handler.handleNotReadable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(AuthErrorCode.VALIDATION_FAILED);
    }

    @Test
    void oauth_authentication_safety_net_maps_to_401_with_oauth_authorization_failed() {
        ResponseEntity<AuthErrorResponse> response =
                handler.handleOAuthAuthentication(new OAuthAuthenticationException("leaked"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo(AuthErrorCode.OAUTH_AUTHORIZATION_FAILED);
    }

    @Test
    void oauth_subclass_exceptions_also_handled_by_safety_net() {
        // OAuthStateInvalidException / OAuthProviderCallFailedException 는
        // OAuthAuthenticationException 의 하위라 같은 핸들러로 매핑
        ResponseEntity<AuthErrorResponse> stateInvalid =
                handler.handleOAuthAuthentication(new OAuthStateInvalidException("x"));
        assertThat(stateInvalid.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<AuthErrorResponse> providerFailed =
                handler.handleOAuthAuthentication(new OAuthProviderCallFailedException("x"));
        assertThat(providerFailed.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void generic_exception_maps_to_500_with_internal_error_and_no_leak() {
        Exception ex = new RuntimeException("INTERNAL_DEBUG_TRACE_secret_value_xyz");

        ResponseEntity<AuthErrorResponse> response = handler.handleGeneric(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo(AuthErrorCode.INTERNAL_ERROR);
        assertThat(response.getBody().message()).doesNotContain("INTERNAL_DEBUG_TRACE");
        assertThat(response.getBody().message()).doesNotContain("secret_value");
    }
}
