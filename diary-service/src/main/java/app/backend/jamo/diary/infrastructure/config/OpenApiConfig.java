package app.backend.jamo.diary.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi 메타데이터 + JWT bearer security scheme 등록.
 *
 * <p>CLAUDE.md "새 서비스 OpenAPI 의무" — diary-service 가 controller 첫 도입 PR
 * (D-a-5-impl-presentation). identity-service 의 OpenApiConfig 패턴 정합 — title / description /
 * scheme name 만 본 서비스 맥락에 맞게 변경.
 *
 * <p>endpoint 별 인증 요구는 controller 에서 {@code @SecurityRequirement(name = "BearerJwt")} 명시
 * (전역 강제 X).
 *
 * <p>swagger-ui / api-docs 는 application.yaml 의 {@code springdoc.*} 로 활성/경로 제어, prod profile
 * 에서는 multi-document override 로 비활성.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME_NAME = "BearerJwt";

    @Bean
    public OpenAPI diaryServiceOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("jamo diary-service API")
                .version("v1")
                .description("diary + comment + validation + diarychat + sentence-feedback (ADR-0002)")
                .contact(new Contact().name("jamo backend")))
            .components(new Components()
                .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Access token (RS256, identity-service 발급)")));
    }
}
