package app.backend.jamo.identity.presentation.web;

import app.backend.jamo.identity.infrastructure.config.DeviceCookieProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceIdResolverTest {

    private DeviceIdResolver resolver;

    @BeforeEach
    void setUp() {
        DeviceCookieProperties props = new DeviceCookieProperties(
                "jamoai.app", true, "Lax", Duration.ofDays(365));
        resolver = new DeviceIdResolver(props);
    }

    @Test
    void resolves_from_header_when_valid_format() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DeviceIdResolver.HEADER_NAME, "device-abc-1234567");

        DeviceIdResult result = resolver.resolve(request);

        assertThat(result.deviceId()).isEqualTo("device-abc-1234567");
        assertThat(result.isNewlyGenerated()).isFalse();
    }

    @Test
    void falls_back_to_cookie_when_header_invalid_format() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DeviceIdResolver.HEADER_NAME, "x");  // length < 8 → invalid
        request.setCookies(new Cookie(DeviceIdResolver.COOKIE_NAME, "cookie-deviceid"));

        DeviceIdResult result = resolver.resolve(request);

        assertThat(result.deviceId()).isEqualTo("cookie-deviceid");
        assertThat(result.isNewlyGenerated()).isFalse();
    }

    @Test
    void falls_back_to_cookie_when_header_absent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(DeviceIdResolver.COOKIE_NAME, "cookie-deviceid"));

        DeviceIdResult result = resolver.resolve(request);

        assertThat(result.deviceId()).isEqualTo("cookie-deviceid");
    }

    @Test
    void generates_new_device_id_when_both_header_and_cookie_absent() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        DeviceIdResult result = resolver.resolve(request);

        assertThat(result.deviceId()).startsWith("web-");
        assertThat(result.isNewlyGenerated()).isTrue();
        assertThat(DeviceIdResolver.DEVICE_ID_PATTERN.matcher(result.deviceId()).matches()).isTrue();
    }

    @Test
    void generates_new_device_id_when_cookie_value_invalid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(DeviceIdResolver.COOKIE_NAME, "x"));  // too short

        DeviceIdResult result = resolver.resolve(request);

        assertThat(result.isNewlyGenerated()).isTrue();
    }

    @Test
    void rejects_log_injection_attempts_in_header() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DeviceIdResolver.HEADER_NAME, "valid-id\nINFO  fake-log");

        DeviceIdResult result = resolver.resolve(request);

        assertThat(result.isNewlyGenerated()).isTrue();  // header rejected
        assertThat(result.deviceId()).startsWith("web-");
    }

    @Test
    void rejects_overly_long_device_id_in_header() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DeviceIdResolver.HEADER_NAME, "a".repeat(65));

        DeviceIdResult result = resolver.resolve(request);

        assertThat(result.isNewlyGenerated()).isTrue();
    }

    @Test
    void set_device_cookie_writes_attributes() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        resolver.setDeviceCookie(response, "device-abcdef");

        String header = response.getHeader("Set-Cookie");
        assertThat(header).contains("jamo_device_id=device-abcdef");
        assertThat(header).contains("HttpOnly");
        assertThat(header).contains("Secure");
        assertThat(header).contains("SameSite=Lax");
        assertThat(header).contains("Path=/");
        assertThat(header).contains("Domain=jamoai.app");
        assertThat(header).contains("Max-Age=31536000");
    }

    @Test
    void set_device_cookie_rejects_invalid_format() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> resolver.setDeviceCookie(response, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deviceId");
    }
}
