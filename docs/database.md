# Database

PostgreSQL. Le tabelle le crea e le modifica solo Flyway (`backend/src/main/resources/db/migration`); Hibernate non tocca lo schema.

## Regole generali

- Chiavi primarie `BIGINT GENERATED ALWAYS AS IDENTITY`.
- Istanti in `TIMESTAMPTZ` (UTC); le date senza ora, come scadenze e date di pagamento, in `DATE`.
- Enum salvati come testo, con un vincolo `CHECK` sui valori ammessi.
- Una colonna `version` sulle tabelle modificabili, per accorgersi delle modifiche concorrenti.
- Le colonne che dicono chi ha fatto cosa (`created_by`, `published_by`, `confirmed_by`…) le riempie solo il server, con l'utente autenticato.
- Importi in centesimi di euro (`INTEGER`), mai con la virgola mobile.
- Gli utenti non si cancellano: si rifiutano o si sospendono. Così i riferimenti restano validi e lo storico è completo.

## Migrazioni

| Versione | Contenuto |
|---|---|
| V1 | `users`, `refresh_tokens`, `user_tokens` |
| V2 | `matches`, `availabilities` |
| V3 | `callups`, `callup_players`, `callup_revisions` |
| V4 | `match_events` |
| V5 | `payment_requests`, `payment_assignments` |
| V6 | `audit_logs` (in sola aggiunta), `email_notifications` |
| V7 | esito della consegna delle email (webhook Mailjet) |
| V8 | nuovo tipo di email `PAYMENT_REMINDER` |

Una migrazione già applicata non si modifica mai: ogni cambiamento va in un file nuovo (`V9__descrizione.sql` e così via). Flyway controlla i checksum e blocca l'avvio se un file vecchio è stato toccato.

## Tabelle

**users** — gli account. Email unica e sempre in minuscolo; ruolo ADMIN, COACH o PLAYER; stato PENDING, ACTIVE, REJECTED o SUSPENDED. Le password sono solo hash BCrypt. `failed_login_attempts` e `locked_until` servono a bloccare l'account dopo troppi tentativi sbagliati.

**refresh_tokens** — le sessioni. Si salva solo l'hash del token. I token della stessa sessione condividono un `family_id`: se qualcuno riusa un token già sostituito, si revoca tutta la famiglia.

**user_tokens** — i link monouso per confermare l'email e reimpostare la password: solo hash, con scadenza e data di utilizzo.

**matches** — le partite: avversario, data e ora, campo, note e stato (programmata, conclusa, annullata). Il risultato ha entrambi i gol o nessuno. Una partita annullata resta, con autore, data e motivo.

**availabilities** — una riga per partita e giocatore. Se la riga non c'è, il giocatore non ha ancora risposto. `admin_override` segna le correzioni fatte dall'Admin dopo la pubblicazione della convocazione.

**callups, callup_players, callup_revisions** — le convocazioni.
- Una convocazione per partita: bozza, pubblicata o ritirata, con ritrovo e note.
- I convocati non si cancellano: chi viene tolto ha `removed_at` e `removed_by`. Ricordando in quale revisione è stato aggiunto o tolto ciascuno, alla pubblicazione si avvisa solo chi è interessato.
- Ogni pubblicazione, modifica o ritiro lascia una revisione immutabile con la fotografia (JSONB) di dettagli e convocati.

**match_events** — il tabellino: gol, autogol, ammonizioni ed espulsioni. `team_side` dice di che squadra è chi compie l'evento; i nostri giocatori sono collegati all'account, gli avversari e chi non ha account hanno solo il nome. Il minuto (0–150) è facoltativo.

| Evento | Squadra di chi lo compie | Gol per |
|---|---|---|
| GOAL | TEAM | noi |
| GOAL | OPPONENT | avversari |
| OWN_GOAL | TEAM | avversari |
| OWN_GOAL | OPPONENT | noi |

Il backend confronta il risultato ufficiale con i gol calcolati dagli eventi e segnala le incoerenze, come due gialli senza rosso o due espulsioni dello stesso giocatore, senza bloccare il salvataggio. Un evento inserito per errore si può eliminare: i suoi dati restano nell'audit log.

Statistiche e storico dei risultati non hanno tabelle proprie: si calcolano al momento dalle partite concluse. I giocatori senza account sono raggruppati per nome, ignorando maiuscole e spazi.

**payment_requests, payment_assignments** — i pagamenti.
- Una richiesta ha descrizione, importo (maggiore di zero, non modificabile dopo la creazione), scadenza, note e stato (aperta, chiusa, annullata).
- Ogni destinatario ha la sua quota: da pagare, pagata o annullata. Una quota pagata deve avere data e autore della conferma.
- La richiesta si chiude da sola quando non resta nessuna quota da pagare e si riapre se si riapre una quota o si aggiunge un destinatario. Annullando la richiesta, le quote da pagare si annullano e quelle pagate restano.

**audit_logs** — il registro delle modifiche. Un trigger impedisce di modificarlo o svuotarlo. Contiene autore, azione, oggetto, dettagli e IP, mai password o token.

**email_notifications** — la coda delle email e il registro degli invii. Una chiave unica impedisce di mettere in coda due volte la stessa email. Contiene stato, tentativi, prossimo tentativo, ultimo errore e id del messaggio Mailjet.

## Cosa si conserva

| Dato | Regola |
|---|---|
| Utenti | mai cancellati, si disattivano con lo stato |
| Partite | annullate, mai cancellate |
| Convocazioni | rimozioni logiche e revisioni immutabili |
| Pagamenti | richieste e quote restano, anche annullate |
| Audit log | solo aggiunte, garantite dal database |
| Email | restano come registro; il testo con i link di reset o verifica si cancella dopo l'invio |
| Token | quelli scaduti da più di 7 giorni si eliminano ogni notte |

## Database locali

`backend/db/init-local.sql` crea l'utente `squadra` e i database `squadra_dev` (sviluppo) e `squadra_test` (test). Le tabelle le crea Flyway al primo avvio.
