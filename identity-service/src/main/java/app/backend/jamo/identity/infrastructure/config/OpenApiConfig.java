package app.backend.jamo.identity.infrastructure.config;

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
 * <p>endpoint 별 인증 요구는 controller 에서 {@code @SecurityRequirement(name = "BearerJwt")}
 * 로 명시한다 (전역 강제는 하지 않음 — public endpoint 가 섞여 있기 때문).
 *
 * <p>swagger-ui / api-docs 는 application.yaml 의 {@code springdoc.*} 로 활성/경로를
 * 제어하며, {@code prod} profile 에서는 비활성된다.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME_NAME = "BearerJwt";

    @Bean
    public OpenAPI identityServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("jamo identity-service API")
                        .version("v1")
                        .description("auth + user + profile (ADR-0001, ADR-0002, ADR-0006)")
                        .contact(new Contact().name("jamo backend")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token (RS256, identity-service 발급)")));
    }
}
