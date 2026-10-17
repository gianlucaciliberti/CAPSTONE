package it.squadra.gestionale.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import it.squadra.gestionale.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Firma e verifica degli access token JWT con HMAC-SHA256 e segreto simmetrico (JWT_SECRET, Base64).
 * Il decoder accetta solo token HS256 emessi da questa applicazione, non scaduti e di tipo "access".
 */
@Configuration
public class JwtConfig {

    /** HS256 richiede una chiave di almeno 256 bit. */
    static final int MIN_SECRET_BYTES = 32;
    static final String TOKEN_TYPE_CLAIM = "token_type";
    static final String ACCESS_TOKEN_TYPE = "access";
    /** Tolleranza per piccole differenze di orologio tra server. */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    @Bean
    public JwtEncoder jwtEncoder(AppProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(properties)));
    }

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
                new JwtClaimValidator<String>(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE::equals)));
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
}
