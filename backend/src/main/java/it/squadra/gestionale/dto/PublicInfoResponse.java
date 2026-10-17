package it.squadra.gestionale.dto;

import java.time.OffsetDateTime;

public record PublicInfoResponse(String application, String apiVersion, String timezone, OffsetDateTime serverTime) {
}
