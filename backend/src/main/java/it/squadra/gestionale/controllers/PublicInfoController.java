package it.squadra.gestionale.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.dto.CommonDto.PublicInfoResponse;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
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
    /** TEMPORANEO (fase 8, parte 4): diagnostica degli IP dietro il proxy di Render. Spenta per default. */
    private final boolean debugClientIp;

    public PublicInfoController(Clock clock, AppProperties properties,
                                @Value("${DEBUG_CLIENT_IP:false}") boolean debugClientIp) {
        this.clock = clock;
        this.properties = properties;
        this.debugClientIp = debugClientIp;
    }

    @Operation(summary = "Stato e informazioni pubbliche del servizio")
    @GetMapping("/info")
    public PublicInfoResponse info(HttpServletRequest request, HttpServletResponse response) {
        if (debugClientIp) {
            response.setHeader("X-Debug-Client-Ip", describeClientIp(request));
        }
        return new PublicInfoResponse("Gestionale Squadra", "v1", properties.timezone().getId(),
                ZonedDateTime.now(clock).toOffsetDateTime());
    }

    /**
     * TEMPORANEO: header di rete della richiesta stessa (chi chiama vede solo i propri indirizzi), letti dalla
     * richiesta originale di Tomcat perché il filtro dei forwarded header li nasconde ai livelli superiori.
     */
    private static String describeClientIp(HttpServletRequest request) {
        ServletRequest raw = request;
        while (raw instanceof ServletRequestWrapper wrapper) {
            raw = wrapper.getRequest();
        }
        HttpServletRequest original = (HttpServletRequest) raw;
        StringBuilder out = new StringBuilder();
        for (String name : new String[] {"X-Forwarded-For", "Forwarded", "X-Real-IP", "True-Client-IP",
                "CF-Connecting-IP", "X-Forwarded-Proto"}) {
            out.append(name).append('=').append(String.join(" | ", Collections.list(original.getHeaders(name))))
                    .append("; ");
        }
        return out.append("socket=").append(original.getRemoteAddr())
                .append("; resolved=").append(request.getRemoteAddr()).toString();
    }
}
