package it.squadra.gestionale.security;

import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.config.RateLimitProperties;
import it.squadra.gestionale.enums.Role;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configurazione di sicurezza: API stateless, tutto chiuso per default.
 * Le richieste si autenticano con un access token JWT nell'header {@code Authorization: Bearer ...};
 * i permessi di dettaglio sono verificati con {@code @PreAuthorize} sui controller.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ProblemDetailsAuthenticationHandlers handlers,
                                                   UserJwtAuthenticationConverter jwtConverter,
                                                   RateLimiter rateLimiter,
                                                   RateLimitProperties rateLimitProperties) throws Exception {
        http
                // Limiti per IP prima di qualsiasi elaborazione (dopo CORS, così anche il 429 ha gli header CORS).
                .addFilterBefore(new RateLimitFilter(rateLimiter, rateLimitProperties),
                        BearerTokenAuthenticationFilter.class)
                .cors(Customizer.withDefaults())
                // CSRF di Spring disattivato: le API usano il Bearer token; gli unici endpoint basati su cookie
                // (refresh e logout) sono protetti da SameSite=Strict e dall'header richiesto da CsrfProtection.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/public/**").permitAll()
                        // refresh e logout si autenticano con il cookie, non con l'access token (che può essere scaduto)
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    /** Ruoli cumulativi: ADMIN ha anche i permessi di COACH, che ha anche quelli di PLAYER. */
    @Bean
    public static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(Role.ADMIN.name()).implies(Role.COACH.name())
                .role(Role.COACH.name()).implies(Role.PLAYER.name())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** CORS restrittivo: solo le origini configurate in CORS_ALLOWED_ORIGINS, con credenziali (cookie di refresh). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", CsrfProtection.HEADER));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
