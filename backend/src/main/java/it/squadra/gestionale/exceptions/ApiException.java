package it.squadra.gestionale.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Eccezione applicativa con stato HTTP e codice stabile. Il messaggio è destinato all'utente finale
 * (in italiano) e non deve contenere dettagli interni.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;

    public ApiException(HttpStatus status, ErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }

    /** Richiesta formalmente valida ma contraria a una regola di business (es. disponibilità bloccata). */
    public static ApiException businessRule(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, message);
    }

    public static ApiException unauthenticated(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }
}
