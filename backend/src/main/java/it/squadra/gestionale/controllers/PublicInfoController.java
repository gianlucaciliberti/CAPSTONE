package it.squadra.gestionale.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.dto.PublicInfoResponse;
import java.time.Clock;
import java.time.ZonedDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Informazioni pubbliche e non sensibili sul backend, usate dal frontend per verificare la connessione. */
@Tag(name = "Sistema")
@RestController
@RequestMapping("/api/v1/public")
public class PublicInfoController {

    private final Clock clock;
    private final AppProperties properties;

    public PublicInfoController(Clock clock, AppProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    @Operation(summary = "Stato e informazioni pubbliche del servizio")
    @GetMapping("/info")
    public PublicInfoResponse info() {
        return new PublicInfoResponse("Gestionale Squadra", "v1", properties.timezone().getId(),
                ZonedDateTime.now(clock).toOffsetDateTime());
    }
}
