package app.backend.jamo.diary.presentation.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controller 핸들러 파라미터에 적용 — Authorization 헤더의 access JWT 를 검증하고
 * {@link AuthenticatedUser} 를 주입한다 ({@link LoginUserArgumentResolver}).
 *
 * <p>인증 실패 (헤더 부재 / 만료 / 위조 / blacklist sid) 는 {@link UnauthorizedException}
 * 로 전파되어 {@code SentenceFeedbackExceptionHandler} 가 401 매핑.
 *
 * <p>identity-service {@code LoginUser} 패턴 정합. 본 PR 시점 diary-service 자체 도입 — common-auth-web
 * 모듈 추출은 두 번째 도메인 controller 등장 시점에 검토 (premature abstraction 회피, 박제
 * decisions/diary/sentence-feedback-presentation-decisions.md).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {
}
