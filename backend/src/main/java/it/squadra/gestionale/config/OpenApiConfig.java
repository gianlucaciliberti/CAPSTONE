package it.squadra.gestionale.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadati della documentazione OpenAPI (Swagger UI su /swagger-ui.html, attiva solo se SWAGGER_ENABLED). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gestionaleOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Gestionale Squadra - API")
                .version("v1")
                .description("API REST del gestionale della squadra di calcio amatoriale."));
    }
}
