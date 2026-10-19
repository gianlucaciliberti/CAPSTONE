package it.squadra.gestionale.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dati del primo Admin (prefisso {@code app.bootstrap-admin}, variabili ADMIN_BOOTSTRAP_*).
 * Usati solo se valorizzati e solo finché non esiste alcun Admin; vanno rimossi dopo il primo avvio.
 */
@ConfigurationProperties(prefix = "app.bootstrap-admin")
public record BootstrapAdminProperties(String email, String password, String firstName, String lastName) {

    public boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
