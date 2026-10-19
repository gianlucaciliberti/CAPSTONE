package it.squadra.gestionale.security;

import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.entities.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Emissione degli access token. Il token contiene solo l'id utente (subject): ruolo e stato
 * vengono riletti dal database a ogni richiesta, così una sospensione ha effetto immediato.
 */
@Service
public class JwtService {

    static final String TOKEN_TYPE_CLAIM = "token_type";
    static final String ACCESS_TOKEN_TYPE = "access";

    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtService(JwtEncoder encoder, Clock clock, AppProperties properties) {
        this.encoder = encoder;
        this.clock = clock;
        this.issuer = properties.security().jwt().issuer();
        this.accessTokenTtl = properties.security().jwt().accessTokenTtl();
    }

    public AccessToken issueAccessToken(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(accessTokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, expiresAt);
    }

    public record AccessToken(String value, Instant expiresAt) {
    }
}
