package it.squadra.gestionale.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import it.squadra.gestionale.services.MatchService;
import it.squadra.gestionale.services.UserService;
import java.time.Clock;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
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

    /**
     * Metadati della documentazione OpenAPI (Swagger UI su /swagger-ui.html, attiva solo se SWAGGER_ENABLED).
     * Il pulsante "Authorize" accetta l'access token restituito da POST /api/v1/auth/login.
     */
    @Bean
    public OpenAPI gestionaleOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Gestionale Squadra - API")
                        .version("v1")
                        .description("API REST del gestionale della squadra di calcio amatoriale."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    /** All'avvio crea il primo Admin se configurato tramite ADMIN_BOOTSTRAP_*. */
    @Bean
    @Order(1)
    public ApplicationRunner adminBootstrapRunner(UserService userService) {
        return args -> userService.createFirstAdminIfConfigured();
    }

    /** Solo profilo dev: utenti e partite dimostrativi se le tabelle sono vuote (mai in produzione). */
    @Bean
    @Order(2)
    @Profile("dev")
    public ApplicationRunner demoDataRunner(UserService userService, MatchService matchService) {
        return args -> {
            userService.createDemoUsersIfEmpty();
            matchService.createDemoMatchesIfEmpty();
        };
    }

    /**
     * All'avvio crea l'account del valutatore se configurato tramite REVIEWER_*. Dopo i dati dimostrativi:
     * questi partono solo con il database vuoto.
     */
    @Bean
    @Order(3)
    public ApplicationRunner reviewerRunner(UserService userService) {
        return args -> userService.createReviewerIfConfigured();
    }
}
