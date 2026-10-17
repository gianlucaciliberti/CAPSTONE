package it.squadra.gestionale.security;

import it.squadra.gestionale.exceptions.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Risposte 401/403 generate dal filtro di sicurezza, nello stesso formato RFC 9457 (application/problem+json)
 * usato dal resto delle API. I messaggi sono fissi: nessun dettaglio interno viene esposto.
 */
@Component
public class ProblemDetailsAuthenticationHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Autenticazione richiesta.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Non hai i permessi per questa operazione.");
    }

    private void write(HttpServletResponse response, HttpStatus status, ErrorCode code, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s","code":"%s"}"""
                .formatted(status.getReasonPhrase(), status.value(), detail, code.name()));
    }
}
