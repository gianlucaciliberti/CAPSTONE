package it.squadra.gestionale.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Invio effettivo di una email. L'implementazione dipende da {@code app.mail.mode}:
 * LOG (nessun invio reale, sviluppo e test) oppure MAILJET (fase 4).
 */
public interface EmailSender {

    /**
     * Invia l'email e restituisce l'id assegnato dal provider (se disponibile).
     * Un'eccezione indica un invio fallito: il worker riproverà più tardi.
     */
    String send(OutgoingEmail email) throws Exception;

    record OutgoingEmail(String toEmail, String toName, String subject, String textBody, String htmlBody,
                         String customId) {
    }

    /**
     * Modalità senza invii reali: l'email viene solo registrata nei log.
     * Il corpo (che può contenere link con token) è scritto solo a livello DEBUG, attivo nel profilo dev.
     */
    @Component
    @ConditionalOnProperty(name = "app.mail.mode", havingValue = "LOG", matchIfMissing = true)
    class LogEmailSender implements EmailSender {

        private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

        @Override
        public String send(OutgoingEmail email) {
            log.info("[MAIL_MODE=LOG] Email non inviata davvero -> a: {} | oggetto: {}", email.toEmail(), email.subject());
            log.debug("[MAIL_MODE=LOG] Testo dell'email per {}:\n{}", email.toEmail(), email.textBody());
            return "log-" + email.customId();
        }
    }
}
