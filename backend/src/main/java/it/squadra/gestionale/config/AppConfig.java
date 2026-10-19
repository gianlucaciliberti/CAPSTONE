package it.squadra.gestionale.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import it.squadra.gestionale.services.UserService;
import java.time.Clock;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bean applicativi generali (sicurezza esclusa, vedi SecurityConfig). Abilita i job periodici (worker email). */
@Configuration
@EnableScheduling
public class AppConfig {

    /**
     * Orologio unico dell'applicazione. Gli istanti sono salvati in UTC; il fuso della squadra
     * (Europe/Rome) si applica quando si ragiona su giorni e orari locali.
     * Iniettare {@link Clock} invece di chiamare {@code Instant.now()} rende il codice testabile.
     */
    @Bean
    public Clock clock(AppProperties properties) {
        return Clock.system(properties.timezone());
    }

    /** Metadati della documentazione OpenAPI (Swagger UI su /swagger-ui.html, attiva solo se SWAGGER_ENABLED). */
    @Bean
    public OpenAPI gestionaleOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Gestionale Squadra - API")
                .version("v1")
                .description("API REST del gestionale della squadra di calcio amatoriale."));
    }

    /** All'avvio crea il primo Admin se configurato tramite ADMIN_BOOTSTRAP_*. */
    @Bean
    public ApplicationRunner adminBootstrapRunner(UserService userService) {
        return args -> userService.createFirstAdminIfConfigured();
    }
}
