package app.backend.jamo.identity.presentation.web;

import app.backend.jamo.identity.infrastructure.config.DeviceCookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * deviceId 결정 (ADR-0006 결정 2 + decisions/auth/cookie-policy.md):
 * <ol>
 *   <li>{@code X-Device-Id} 헤더 (형식 검증 통과 시)</li>
 *   <li>{@code jamo_device_id} cookie (형식 검증 통과 시)</li>
 *   <li>새로 {@code web-{UUID}} 생성 — 결과의 {@code isNewlyGenerated=true},
 *       controller 가 {@link #setDeviceCookie} 로 응답에 cookie 발급</li>
 * </ol>
 *
 * <p>형식 검증 ({@link #DEVICE_ID_PATTERN}): {@code ^[A-Za-z0-9_-]{8,64}$} —
 * log injection / 비정상 길이 / 제어문자 차단 (security review M3).
 */
@Component
public class DeviceIdResolver {

    public static final String HEADER_NAME = "X-Device-Id";
    public static final String COOKIE_NAME = "jamo_device_id";
    public static final String COOKIE_PATH = "/";
    static final String GENERATED_DEVICE_PREFIX = "web-";
    static final Pattern DEVICE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private final DeviceCookieProperties cookieProperties;

    public DeviceIdResolver(DeviceCookieProperties cookieProperties) {
        this.cookieProperties = cookieProperties;
    }

    public DeviceIdResult resolve(HttpServletRequest request) {
        String header = request.getHeader(HEADER_NAME);
        if (isValid(header)) {
            return new DeviceIdResult(header, false);
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName()) && isValid(cookie.getValue())) {
                    return new DeviceIdResult(cookie.getValue(), false);
                }
            }
        }

        return new DeviceIdResult(GENERATED_DEVICE_PREFIX + UUID.randomUUID(), true);
    }

    public void setDeviceCookie(HttpServletResponse response, String deviceId) {
        if (!isValid(deviceId)) {
            throw new IllegalArgumentException("invalid deviceId format: refusing to set cookie");
        }
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, deviceId)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(COOKIE_PATH)
                .maxAge(cookieProperties.maxAge());
        if (cookieProperties.domain() != null && !cookieProperties.domain().isBlank()) {
            builder.domain(cookieProperties.domain());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    private static boolean isValid(String value) {
        return value != null && DEVICE_ID_PATTERN.matcher(value).matches();
    }
}
