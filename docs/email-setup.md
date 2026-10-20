# Email (Mailjet)

Stato: sono pronti la coda (`EmailService`), il worker con retry, la modalità `LOG` (`EmailSender.LogEmailSender`), i template Thymeleaf e le notifiche delle convocazioni. L'invio reale con Mailjet, i webhook e il retry manuale da parte dell'Admin arrivano nella **parte 3 della fase 4**: fino ad allora `MAIL_MODE=MAILJET` non è ancora supportato (il backend non si avvia).

## Template (Thymeleaf)

I template sono in `backend/src/main/resources/templates/email/` e vengono elaborati da `EmailTemplateService` con il solo motore Thymeleaf (nessuna pagina web):

- `layout.html`: struttura comune (intestazione con il nome della squadra, contenuto, piè di pagina) e i fragment `button` e `matchDetails`. Layout a tabelle con stili inline e larghezza massima 600px: è il formato più compatibile con Gmail, Outlook e le app mobili.
- Per ogni email una coppia `<nome>.html` / `<nome>.txt` (versione testuale, modalità TEXT di Thymeleaf): `callup-published`, `callup-updated`, `callup-removed`, `callup-withdrawn`, `account-approved`, `email-verification`, `password-reset`.
- I valori sono sempre inseriti con `th:text`, che applica l'escape HTML: note e nomi scritti dagli utenti non possono iniettare codice. Le date sono già formattate in italiano e nel fuso Europe/Rome.
- Variabili comuni: `firstName`, `subject`, `teamName` (= `MAIL_FROM_NAME`), `frontendUrl`.

Per modificare un testo basta cambiare il template; `EmailTemplateServiceTest` verifica che ogni template produca sia la versione HTML sia quella testuale.

## Notifiche delle convocazioni

Le email vengono accodate nella stessa transazione della revisione della convocazione e partono solo verso gli utenti interessati:

| Evento | Chi riceve l'email | Template |
|---|---|---|
| Pubblicazione (anche dopo un ritiro) | tutti i convocati | `callup-published` |
| Modifica: utente aggiunto | il nuovo convocato | `callup-published` |
| Modifica: utente rimosso | il rimosso (se era già stato avvisato) | `callup-removed` |
| Modifica di ritrovo, note o dettagli della partita | gli altri convocati | `callup-updated` |
| Modifica del solo elenco | nessun altro convocato | — |
| Ritiro della convocazione | tutti i convocati | `callup-withdrawn` |
| Partita modificata dall'Admin (data, luogo...) | tutti i convocati, con nuova revisione | `callup-updated` |
| Partita annullata | convocazione ritirata automaticamente, avviso a tutti | `callup-withdrawn` |

Le bozze non generano email. Ogni email contiene i dettagli della partita, il link alla mappa e il link alla convocazione (`FRONTEND_URL/matches/{id}`).

In modalità `LOG` il log a livello INFO riporta solo destinatario e oggetto; il testo completo (che può contenere link con token) compare solo a livello DEBUG, attivo nel profilo `dev`.

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

## Affidabilità

- **Transactional outbox**: l'email viene inserita in `email_notifications` nella stessa transazione dell'operazione (es. pubblicazione della convocazione); `EmailService.enqueue` rifiuta di funzionare fuori da una transazione. Se la transazione fallisce non parte nessuna email; se riesce, l'email non può andare persa.
- **Worker**: ogni `MAIL_WORKER_INTERVAL` (default 10 secondi) prende fino a 20 righe `PENDING` con `next_attempt_at` scaduto usando `SELECT ... FOR UPDATE SKIP LOCKED`: le righe restano bloccate finché l'esito non è salvato, quindi due esecuzioni (o due istanze) non inviano la stessa email in parallelo. Garanzia "almeno una volta": un crash tra invio e salvataggio può causare un reinvio.
- **Retry limitati** con attesa esponenziale (30s, 1m, 2m, 4m... massimo 1 ora) fino a `MAIL_MAX_ATTEMPTS`; poi stato `FAILED`. Il retry manuale da parte dell'Admin (registrato nell'audit log) arriva nella fase 4.
- **Idempotenza**: `idempotency_key` univoca (es. `CALLUP_PUBLISHED:c<callupId>:r<revisione>:u<userId>`) impedisce invii duplicati anche in caso di doppio clic o retry della richiesta.
- **Dati sensibili**: i payload con link di reset password e di verifica email vengono ripuliti dopo l'invio.
- **Webhook** (facoltativi): se attivati, l'endpoint verifica un segreto condiviso (`MAILJET_WEBHOOK_SECRET`) e aggiorna lo stato di consegna.
