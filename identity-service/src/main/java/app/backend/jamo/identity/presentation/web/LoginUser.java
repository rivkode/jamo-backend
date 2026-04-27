package app.backend.jamo.identity.presentation.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controller 핸들러 파라미터에 적용 — Authorization 헤더의 access JWT 를 검증하고
 * {@link AuthenticatedUser} 를 주입한다 ({@link LoginUserArgumentResolver}).
 *
 * <p>인증 실패 (헤더 부재 / 만료 / 위조 / blacklist sid) 는 {@code UnauthorizedException}
 * 로 전파되어 {@code AuthExceptionHandler} 가 401 + {@code UNAUTHORIZED} 매핑.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {
}
