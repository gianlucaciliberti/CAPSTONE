# Email (Mailjet)

Stato: completo. Coda persistente con worker e retry (`EmailService`), template Thymeleaf, notifiche delle convocazioni e dei pagamenti (con promemoria), invio reale con Mailjet (`EmailSender.MailjetEmailSender`), modalità di test senza invii (`LOG`), webhook degli eventi di consegna, pagina dello stato degli invii con retry e annullamento manuali (`EmailAdminService`).

## Stato degli invii e gestione dei falliti

- Pagina **Email** del frontend (`/emails`), per Allenatori e Admin: elenco degli invii filtrabile per stato e per convocazione, con tentativi, data di invio ed esito della consegna (dal webhook). Il contenuto delle email non è mai esposto; gli errori tecnici del provider sono visibili solo all'Admin.
- Nel riquadro della convocazione: riepilogo "N inviate · N in coda · N fallite" con link al dettaglio.
- Solo l'Admin può **ritentare** un invio fallito (nuovo ciclo di tentativi), **ritentare tutti i falliti** o **annullare** un invio in coda/fallito. Ogni operazione è registrata nell'audit log (`EMAIL_RETRIED`, `EMAIL_CANCELLED`).

API: `GET /api/v1/emails`, `GET /api/v1/emails/summary`, `POST /api/v1/emails/{id}/retry`, `POST /api/v1/emails/retry-failed`, `POST /api/v1/emails/{id}/cancel`.

## Webhook Mailjet (facoltativo)

Il webhook registra l'esito della consegna (`sent`, `open`, `click`, `bounce`, `blocked`, `spam`, `unsub`) sull'email corrispondente, trovata tramite `MessageID` o `CustomID` (la nostra chiave di idempotenza, inviata con ogni messaggio).

1. Scegliere un segreto lungo e casuale e impostarlo sul backend: `MAILJET_WEBHOOK_SECRET=...`. Senza segreto il webhook è disattivato (risponde 404).
2. Nel pannello Mailjet (*Event API / Webhook*) indicare l'URL con autenticazione Basic, come raccomandato da Mailjet:
   `https://mailjet:<segreto>@<backend>/api/v1/webhooks/mailjet`
   Il nome utente è libero; viene verificata la password (confronto a tempo costante).
3. Selezionare gli eventi desiderati; è consigliato attivare il raggruppamento degli eventi (*group events*): il backend accetta sia un singolo oggetto sia un array.
4. Il backend risponde 200 anche per eventi non riconosciuti o messaggi sconosciuti, così Mailjet non li reinvia; 401 se le credenziali sono errate. Gli eventi più vecchi di quello già registrato non sovrascrivono lo stato più recente.

Il formato degli eventi e la protezione con autenticazione Basic seguono la documentazione ufficiale Mailjet (Event API / webhooks), verificata durante lo sviluppo.

## Template (Thymeleaf)

I template sono in `backend/src/main/resources/templates/email/` e vengono elaborati da `EmailTemplateService` con il solo motore Thymeleaf (nessuna pagina web):

- `layout.html`: struttura comune nello stile dell'app (vedi [`DESIGN.md`](../DESIGN.md)): intestazione verde campo con il marchio "VALHALLA FC" e l'oggetto dell'email in maiuscolo, contenuto su fondo bianco, piè di pagina con il mittente (`MAIL_FROM_NAME`). Fragment: `button` (verde campo con testo lime, come il pulsante primario), `fallbackLink` (link da copiare se il pulsante non funziona), `matchDetails` (la partita come un pezzo di campo: avversario, data e ora in lime, campo, ritrovo e note) e `paymentDetails` (descrizione, importo a grande scala, quota scaduta in rosso, scadenza e note). Layout a tabelle con stili inline, colori ripetuti nell'attributo `bgcolor` e larghezza massima 600px: è il formato più compatibile con Gmail, Outlook e le app mobili. Niente immagini, emoji né web font.
- Per ogni email una coppia `<nome>.html` / `<nome>.txt` (versione testuale, modalità TEXT di Thymeleaf): `callup-published`, `callup-updated`, `callup-removed`, `callup-withdrawn`, `account-approved`, `email-verification`, `password-reset`, `payment-request`, `payment-reminder`.
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

## Notifiche dei pagamenti

| Evento | Chi riceve l'email | Template | Chiave di idempotenza |
|---|---|---|---|
| Nuova richiesta o destinatario aggiunto | il destinatario | `payment-request` | `PAYMENT_REQUEST:p<richiesta>:u<utente>` |
| Scadenza entro 3 giorni (job delle 9:00) | chi deve ancora pagare | `payment-reminder` | `PAYMENT_REMINDER:a<quota>:soon:<scadenza>` |
| Quota scaduta (job delle 9:00) | chi deve ancora pagare | `payment-reminder` | `PAYMENT_REMINDER:a<quota>:overdue:<scadenza>` |
| Promemoria manuale dell'Admin | chi deve ancora pagare | `payment-reminder` | `PAYMENT_REMINDER:a<quota>:manual:<giorno>` |

- Il job automatico (`PaymentService.sendScheduledReminders`, ogni giorno alle 9:00 Europe/Rome) considera solo le quote da pagare di richieste aperte con scadenza. Grazie alla chiave ogni promemoria parte una sola volta, anche se il job gira più volte o su più istanze; se l'Admin sposta la scadenza, i promemoria ripartono per la nuova data.
- Il promemoria manuale (`POST /api/v1/payments/{id}/remind`) parte al massimo una volta al giorno per quota ed è registrato nell'audit log (`PAYMENT_REMINDERS_SENT`).
- Gli utenti non più attivi (es. sospesi) non ricevono promemoria. Le email contengono descrizione, importo, scadenza, note della richiesta e il link a `FRONTEND_URL/payments`, mai la nota interna dell'Admin.
- Nel dettaglio della richiesta l'Admin vede il riepilogo degli invii, con link alla pagina **Email** filtrata (`relatedType=PAYMENT_REQUEST`).

In modalità `LOG` il log a livello INFO riporta solo destinatario e oggetto; il testo completo (che può contenere link con token) compare solo a livello DEBUG, attivo nel profilo `dev`.

## Modalità

| `MAIL_MODE` | Comportamento |
|---|---|
| `LOG` (default) | Nessun invio reale: le email vengono scritte nei log del backend. Usata in sviluppo e nei test. |
| `MAILJET` | Invio reale tramite Mailjet Send API v3.1 (autenticazione Basic con API key e secret key, timeout di 10s in connessione e 20s in lettura). Richiede `MAILJET_API_KEY` e `MAILJET_API_SECRET`: se mancano il backend non si avvia. |

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
- **Retry limitati** con attesa esponenziale (30s, 1m, 2m, 4m... massimo 1 ora) fino a `MAIL_MAX_ATTEMPTS`; poi stato `FAILED`, ritentabile manualmente dall'Admin (vedi sopra).
- **Idempotenza**: `idempotency_key` univoca (es. `CALLUP_PUBLISHED:c<callupId>:r<revisione>:u<userId>`) impedisce invii duplicati anche in caso di doppio clic o retry della richiesta.
- **Dati sensibili**: i payload con link di reset password e di verifica email vengono ripuliti dopo l'invio.
