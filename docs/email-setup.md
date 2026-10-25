# Email

Le email partono da una coda nel database, con tentativi ripetuti se qualcosa va storto. In produzione le invia Mailjet; in sviluppo e nei test si limitano a comparire nei log.

## Modalità

| `MAIL_MODE` | Cosa succede |
|---|---|
| `LOG` (predefinita) | nessun invio: nei log compaiono destinatario e oggetto, e il testo completo solo a livello DEBUG (attivo nel profilo `dev`) |
| `MAILJET` | invio vero con la Send API v3.1 di Mailjet. Servono `MAILJET_API_KEY` e `MAILJET_API_SECRET`, altrimenti il backend non parte |

## Configurare Mailjet

1. Crea un account Mailjet.
2. Verifica l'indirizzo o il dominio del mittente (`MAIL_FROM_EMAIL`) e configura SPF e DKIM, così le email non finiscono nello spam.
3. Genera una coppia di chiavi API.
4. Impostale sul backend, mai nel repository:
   ```
   MAIL_MODE=MAILJET
   MAIL_FROM_EMAIL=convocazioni@tuodominio.it
   MAIL_FROM_NAME=Nome Squadra
   MAILJET_API_KEY=...
   MAILJET_API_SECRET=...
   ```
5. Controlla sul sito di Mailjet quante email al giorno permette il tuo piano.

## Quando parte un'email

**Convocazioni.** Le bozze non mandano niente. Per il resto si avvisa solo chi è interessato:

| Cosa succede | Chi riceve | Template |
|---|---|---|
| Pubblicazione (anche dopo un ritiro) | tutti i convocati | `callup-published` |
| Aggiunta di un giocatore | il nuovo convocato | `callup-published` |
| Rimozione di un giocatore | chi è stato tolto, se era già stato avvisato | `callup-removed` |
| Cambio di ritrovo, note o dettagli della partita | gli altri convocati | `callup-updated` |
| Cambio del solo elenco | nessun altro | — |
| Ritiro della convocazione | tutti i convocati | `callup-withdrawn` |
| L'Admin modifica la partita (data, campo…) | tutti i convocati | `callup-updated` |
| L'Admin annulla la partita | tutti: la convocazione viene ritirata | `callup-withdrawn` |

Ogni email ha i dettagli della partita, il link alla mappa e quello alla convocazione.

**Pagamenti.**

| Cosa succede | Chi riceve | Template |
|---|---|---|
| Nuova richiesta o nuovo destinatario | il destinatario | `payment-request` |
| Scadenza entro 3 giorni | chi deve ancora pagare | `payment-reminder` |
| Quota scaduta | chi deve ancora pagare | `payment-reminder` |
| Promemoria a mano dall'Admin | chi deve ancora pagare | `payment-reminder` |

I promemoria automatici partono ogni giorno alle 9:00 e ognuno arriva una volta sola, anche se il controllo gira più volte; se l'Admin sposta la scadenza ripartono per la nuova data. Quello manuale si può mandare al massimo una volta al giorno per quota e finisce nell'audit log. Chi è sospeso non riceve promemoria, e la nota interna dell'Admin non compare mai nelle email.

**Account.** Conferma dell'indirizzo email (`email-verification`), reimpostazione della password (`password-reset`) e approvazione dell'account (`account-approved`).

## Template

Stanno in `backend/src/main/resources/templates/email/` e li compone Thymeleaf. Ogni email ha due versioni: `<nome>.html` e `<nome>.txt`, solo testo.

`layout.html` contiene la parte comune, nello stile dell'app (vedi [`DESIGN.md`](../DESIGN.md)): intestazione verde campo con "VALHALLA FC" e l'oggetto, contenuto su fondo bianco, piè di pagina con il mittente. Ci sono anche i pezzi riutilizzabili:

- `button`: il pulsante verde con il testo lime;
- `fallbackLink`: il link da copiare se il pulsante non funziona;
- `matchDetails`: la partita come un pezzo di campo, con avversario, data, campo, ritrovo e note;
- `paymentDetails`: la quota, con importo grande e l'avviso se è scaduta.

Il layout usa tabelle e stili scritti dentro i tag, perché è l'unico modo che funziona bene in Gmail, Outlook e nelle app di posta; niente immagini, emoji o font scaricati. I valori si inseriscono sempre con `th:text`, che neutralizza l'HTML: un nome o una nota scritti da un utente non possono iniettare codice.

Variabili sempre disponibili: `firstName`, `subject`, `teamName` (cioè `MAIL_FROM_NAME`) e `frontendUrl`.

Per cambiare un testo basta modificare il template. `EmailTemplateServiceTest` controlla che ogni email venga prodotta in entrambe le versioni.

## Perché le email non si perdono

- **Coda nel database.** L'email si salva in `email_notifications` nella stessa transazione dell'operazione che la genera. Se l'operazione fallisce non parte niente; se riesce, l'email non si può perdere.
- **Invio.** Ogni 10 secondi il backend prende fino a 20 email in attesa e le blocca mentre le invia (`FOR UPDATE SKIP LOCKED`), così due processi non mandano la stessa email. Se il backend si ferma tra l'invio e il salvataggio, un'email può partire due volte.
- **Nuovi tentativi** con attese crescenti (30 secondi, 1 minuto, 2, 4… fino a un'ora), fino a `MAIL_MAX_ATTEMPTS`. Poi l'email risulta fallita e l'Admin può ritentarla.
- **Niente doppioni.** Ogni email ha una chiave unica (per esempio convocazione, revisione e giocatore), così un doppio clic non manda due messaggi.
- **Dati sensibili.** Il testo delle email con i link di reset o di verifica si cancella dopo l'invio.

## Stato degli invii

Nella pagina **Email** (`/emails`) Allenatori e Admin vedono gli invii: stato, tentativi, data ed esito della consegna. Si possono filtrare per stato e per convocazione. Il contenuto delle email non si vede; i messaggi di errore del provider li vede solo l'Admin. Nel riquadro della convocazione c'è un riepilogo ("N inviate · N in coda · N fallite"), e lo stesso vale nel dettaglio di una richiesta di pagamento.

Solo l'Admin può ritentare un invio fallito, ritentarli tutti o annullare un invio in coda. Ogni operazione finisce nell'audit log.

API: `GET /api/v1/emails`, `GET /api/v1/emails/summary`, `POST /api/v1/emails/{id}/retry`, `POST /api/v1/emails/retry-failed`, `POST /api/v1/emails/{id}/cancel`.

## Webhook di Mailjet (facoltativo)

Con il webhook, Mailjet comunica cosa è successo a ogni email: consegnata, aperta, cliccata, rimbalzata, bloccata, segnalata come spam o disiscritta. L'esito si vede nella pagina Email.

1. Imposta sul backend un segreto lungo e casuale: `MAILJET_WEBHOOK_SECRET=...`. Senza segreto il webhook è spento e risponde 404.
2. Nel pannello Mailjet (*Event API*) indica l'URL con utente e password, come consiglia Mailjet:
   `https://mailjet:<segreto>@<backend>/api/v1/webhooks/mailjet`
   Il nome utente è libero; conta solo la password.
3. Scegli gli eventi che ti interessano. Puoi attivare il raggruppamento (*group events*): il backend accetta sia un evento singolo sia un elenco.

Il backend risponde 200 anche agli eventi che non riconosce, così Mailjet non li rimanda, e 401 se la password è sbagliata. Un evento più vecchio non sovrascrive uno più recente.
