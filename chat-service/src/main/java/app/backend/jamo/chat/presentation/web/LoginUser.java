package app.backend.jamo.chat.presentation.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controller 핸들러 파라미터에 적용 — Authorization 헤더의 access JWT 를 검증하고
 * {@link AuthenticatedUser} 를 주입한다 ({@link LoginUserArgumentResolver}).
 *
 * <p>인증 실패는 {@link UnauthorizedException} → {@code ChatExceptionHandler} 가 401 매핑.
 * identity/diary {@code LoginUser} 패턴 정합 (common-auth-web 추출은 후속).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {
}
