package it.squadra.gestionale.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.entities.User.Role;
import it.squadra.gestionale.exceptions.ApiException.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
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

    /** HS256 richiede una chiave di almeno 256 bit. */
    static final int MIN_SECRET_BYTES = 32;
    /** Tolleranza per piccole differenze di orologio tra server. */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserJwtAuthenticationConverter jwtConverter,
                                                   AppProperties properties, Clock clock) throws Exception {
        ProblemHandlers handlers = new ProblemHandlers();
        http
                // Limiti per IP prima di qualsiasi elaborazione (dopo CORS, così anche il 429 ha gli header CORS).
                .addFilterBefore(new RateLimitFilter(clock, properties.rateLimit()),
                        BearerTokenAuthenticationFilter.class)
                .cors(Customizer.withDefaults())
                // CSRF di Spring disattivato: le API usano il Bearer token; gli unici endpoint basati su cookie
                // (refresh e logout) sono protetti da SameSite=Strict e dall'header richiesto da AuthTokens.
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
                                "/api/v1/auth/refresh", "/api/v1/auth/logout",
                                "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").permitAll()
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
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", AuthTokens.CSRF_HEADER));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    // --- JWT: firma e verifica con HMAC-SHA256 e segreto simmetrico (JWT_SECRET, Base64) ---

    @Bean
    public JwtEncoder jwtEncoder(AppProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(properties)));
    }

    /** Accetta solo token HS256 emessi da questa applicazione, non scaduti e di tipo "access". */
    @Bean
    public JwtDecoder jwtDecoder(AppProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(CLOCK_SKEW);
        timestampValidator.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtIssuerValidator(properties.security().jwt().issuer()),
                new JwtClaimValidator<String>(JwtService.TOKEN_TYPE_CLAIM, JwtService.ACCESS_TOKEN_TYPE::equals)));
        return decoder;
    }

    static SecretKey signingKey(AppProperties properties) {
        String secret = properties.security().jwt().secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET non configurato: impostare un segreto Base64 di almeno 256 bit.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET non è una stringa Base64 valida.", e);
        }
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET troppo corto: servono almeno " + MIN_SECRET_BYTES + " byte.");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    // --- Risposte di errore scritte dai filtri, nello stesso formato RFC 9457 del resto delle API ---

    /** Scrive una risposta application/problem+json. I messaggi sono fissi: nessun dettaglio interno esposto. */
    static void writeProblem(HttpServletResponse response, HttpStatus status, ErrorCode code, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s","code":"%s"}"""
                .formatted(status.getReasonPhrase(), status.value(), detail, code.name()));
    }

    /** Risposte 401/403 generate dal filtro di sicurezza. */
    private static final class ProblemHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response,
                             AuthenticationException authException) throws IOException {
            writeProblem(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Autenticazione richiesta.");
        }

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                           AccessDeniedException accessDeniedException) throws IOException {
            writeProblem(response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                    "Non hai i permessi per questa operazione.");
        }
    }
}
