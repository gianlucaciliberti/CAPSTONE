# Email (Mailjet)

Stato: in fase 1 sono pronti la configurazione e la tabella `email_notifications`. Il servizio di invio, i template e il worker arrivano nella **fase 4**.

## Modalità

| `MAIL_MODE` | Comportamento |
|---|---|
| `LOG` (default) | Nessun invio reale: le email vengono scritte nei log del backend. Usata in sviluppo e nei test. |
| `MAILJET` | Invio reale tramite Mailjet Send API v3.1. Richiede `MAILJET_API_KEY` e `MAILJET_API_SECRET`. |

## Configurazione Mailjet

1. Creare un account su Mailjet.
2. Verificare il dominio o l'indirizzo mittente (`MAIL_FROM_EMAIL`) e configurare SPF/DKIM sul DNS del dominio, per non finire nello spam.
3. Generare una coppia API key / secret key dal pannello Mailjet.
4. Impostare sul servizio backend (mai nel repository):
   ```
   MAIL_MODE=MAILJET
   MAIL_FROM_EMAIL=convocazioni@tuodominio.it
   MAIL_FROM_NAME=Nome Squadra
   MAILJET_API_KEY=...
   MAILJET_API_SECRET=...
   ```
5. Verificare i limiti di invio del piano Mailjet scelto direttamente sul sito Mailjet prima di andare in produzione.

## Affidabilità (design, fase 4)

- **Transactional outbox**: l'email viene inserita in `email_notifications` nella stessa transazione dell'operazione (es. pubblicazione della convocazione). Se la transazione fallisce non parte nessuna email; se riesce, l'email non può andare persa.
- **Worker**: un job periodico prende in carico le righe `PENDING` con `next_attempt_at` scaduto, le marca `SENDING` con `locked_until` e le invia.
- **Retry limitati** con backoff esponenziale fino a `MAIL_MAX_ATTEMPTS`; poi stato `FAILED`, visibile all'Admin che può ritentare manualmente (azione registrata nell'audit log).
- **Idempotenza**: `idempotency_key` univoca (es. `CALLUP_PUBLISHED:<callupId>:r<revisione>:user:<userId>`) impedisce invii duplicati anche in caso di doppio clic o retry della richiesta.
- **Dati sensibili**: i payload con link di reset password vengono ripuliti dopo l'invio.
- **Webhook** (facoltativi): se attivati, l'endpoint verifica un segreto condiviso (`MAILJET_WEBHOOK_SECRET`) e aggiorna lo stato di consegna.
