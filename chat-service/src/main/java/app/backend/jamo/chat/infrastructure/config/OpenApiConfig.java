package app.backend.jamo.chat.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi 메타 + JWT bearer scheme. CLAUDE.md "새 서비스 OpenAPI 의무" (controller 첫 도입 PR).
 * endpoint 인증은 controller 의 {@code @SecurityRequirement(name="BearerJwt")} 명시. prod 비활성은 application.yaml.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME_NAME = "BearerJwt";

    @Bean
    public OpenAPI chatServiceOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("jamo chat-service API")
                .version("v1")
                .description("AI 비즈니스 게이트웨이 — STT/TTS (ADR-0003). chat 14 API 는 후속.")
                .contact(new Contact().name("jamo backend")))
            .components(new Components()
                .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Access token (RS256, identity-service 발급)")));
    }
}
