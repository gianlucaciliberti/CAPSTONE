package it.squadra.gestionale.dto;

import java.time.Instant;

/**
 * Risposta di login/rinnovo. L'access token va tenuto solo in memoria dal frontend;
 * il refresh token NON compare qui: viaggia esclusivamente nel cookie HttpOnly.
 */
public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {

    public LoginResponse(String accessToken, Instant expiresAt, UserResponse user) {
        this(accessToken, "Bearer", expiresAt, user);
    }
}
