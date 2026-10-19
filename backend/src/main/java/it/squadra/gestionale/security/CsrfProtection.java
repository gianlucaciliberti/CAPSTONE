package it.squadra.gestionale.security;

import it.squadra.gestionale.exceptions.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Protezione CSRF per gli endpoint autenticati dal cookie del refresh token (refresh e logout).
 * <p>
 * Oltre a SameSite=Strict, queste richieste devono contenere l'header {@value #HEADER}: un sito esterno
 * non può aggiungerlo senza una richiesta preflight CORS, che il backend rifiuta per le origini non ammesse.
 * Le altre API usano l'header Authorization (Bearer), che il browser non invia mai da solo.
 */
@Component
public class CsrfProtection {

    public static final String HEADER = "X-Requested-With";
    public static final String EXPECTED_VALUE = "XMLHttpRequest";

    public void requireCustomHeader(HttpServletRequest request) {
        if (!EXPECTED_VALUE.equals(request.getHeader(HEADER))) {
            throw ApiException.forbidden("Richiesta non valida.");
        }
    }
}
