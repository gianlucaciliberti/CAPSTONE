# Database

PostgreSQL, schema gestito da **Flyway** (`backend/src/main/resources/db/migration`). Hibernate è in modalità `validate`: non crea né modifica tabelle.

## Regole generali

- Chiavi primarie `BIGINT GENERATED ALWAYS AS IDENTITY`.
- Istanti in `TIMESTAMPTZ` (UTC); date senza ora (`due_date`, `paid_on`) in `DATE`.
- Enum salvati come `VARCHAR` con vincolo `CHECK` sui valori ammessi.
- Colonna `version` per l'optimistic locking sulle tabelle modificabili.
- Colonne di autore (`created_by`, `updated_by`, `published_by`, `confirmed_by`...) valorizzate **solo dal server** con l'utente autenticato.
- Gli utenti non vengono mai cancellati (si usa lo stato `REJECTED`/`SUSPENDED`): le foreign key verso `users` restano valide e lo storico è completo.
- Importi in centesimi di euro (`INTEGER`), mai in virgola mobile.

## Migrazioni

| Versione | Contenuto |
|---|---|
| V1 | `users`, `refresh_tokens`, `user_tokens` |
| V2 | `matches`, `availabilities` |
| V3 | `callups`, `callup_players`, `callup_revisions` |
| V4 | `match_events` |
| V5 | `payment_requests`, `payment_assignments` |
| V6 | `audit_logs` (con trigger append-only), `email_notifications` |

Una migrazione già applicata **non va mai modificata**: ogni cambiamento richiede un nuovo file `V7__descrizione.sql`, ecc. Flyway verifica il checksum e blocca l'avvio se un file applicato è stato alterato.

## Tabelle

### users
Account. `email` univoca e salvata in minuscolo (vincolo `CHECK`). `role` ∈ ADMIN/COACH/PLAYER (default PLAYER), `status` ∈ PENDING/ACTIVE/REJECTED/SUSPENDED (default PENDING). `failed_login_attempts` e `locked_until` servono al blocco temporaneo dopo troppi tentativi. `password_hash` contiene solo hash BCrypt.

### refresh_tokens
Sessioni. Solo hash SHA-256 del token (`token_hash`, univoco). `family_id` raggruppa i token ruotati della stessa sessione: il riuso di un token già sostituito revoca l'intera famiglia. `replaced_by_id` collega la catena di rotazione.

### user_tokens
Token monouso per verifica email e reset password (`purpose`), solo hash, con scadenza e `used_at`.

### matches
Partite: avversario, `kickoff_at`, luogo e indirizzo, note, `status` ∈ SCHEDULED/COMPLETED/CANCELLED. Risultato in `team_goals`/`opponent_goals` (entrambi valorizzati o entrambi nulli, non negativi). Annullamento con autore, data e motivo; la partita annullata non viene cancellata.

### availabilities
Una riga per (partita, utente) — vincolo univoco. Nessuna riga = "in attesa di risposta". `admin_override` marca le correzioni amministrative dopo la pubblicazione della convocazione.

### callups / callup_players / callup_revisions
- `callups`: una per partita (vincolo univoco su `match_id`), stato DRAFT/PUBLISHED/WITHDRAWN, ritrovo facoltativo, note, autore e data di pubblicazione e ritiro, `current_revision`.
- `callup_players`: convocati. La rimozione è logica (`removed_at`, `removed_by`); un indice univoco parziale impedisce di avere due righe attive per lo stesso utente. `added_in_revision`/`removed_in_revision` restano nulli finché la modifica è in bozza: alla pubblicazione permettono di notificare solo gli utenti interessati.
- `callup_revisions`: una riga immutabile per ogni pubblicazione, modifica o ritiro, con `snapshot` JSONB di dettagli e convocati, autore e data.

### match_events
Tabellino: `event_type` ∈ GOAL/OWN_GOAL/YELLOW_CARD/RED_CARD, `team_side` ∈ TEAM/OPPONENT (squadra del giocatore che compie l'evento), `player_id` per i nostri giocatori con account oppure `player_name` libero (avversari, giocatori senza account). Minuto 0–150 facoltativo. Gli avversari non possono avere `player_id` (vincolo `CHECK`).

| Evento | Squadra del giocatore | Gol assegnato a |
|---|---|---|
| GOAL | TEAM | noi |
| GOAL | OPPONENT | avversario |
| OWN_GOAL | TEAM | avversario |
| OWN_GOAL | OPPONENT | noi |

Il confronto tra risultato ufficiale (`matches`) e gol calcolati dagli eventi permette di segnalare incoerenze (`GET /api/v1/matches/{id}/scoresheet`, campi `consistent` e `warnings`); vengono segnalate anche doppie ammonizioni senza espulsione ed espulsioni multiple dello stesso giocatore. Gli eventi inseriti per errore si possono eliminare: i loro dati restano nell'audit log.

Lo storico (`GET /api/v1/matches/results` e `/stats`) è calcolato al volo, senza tabelle aggiuntive, dalle sole partite `COMPLETED`: bilancio vittorie/pareggi/sconfitte e gol dal risultato ufficiale, statistiche dei nostri giocatori (gol, autogol, ammonizioni, espulsioni) dagli eventi `TEAM`. I giocatori senza account sono raggruppati per nome, senza distinguere maiuscole e spazi.

### payment_requests / payment_assignments
- `payment_requests`: descrizione, `amount_cents` > 0, valuta EUR, scadenza, note, stato OPEN/CLOSED/CANCELLED. Mai cancellate.
- `payment_assignments`: quota per utente (univoca per richiesta+utente), stato PENDING/PAID/CANCELLED. Lo stato PAID richiede data pagamento, data e autore della conferma (vincolo `CHECK`).

### audit_logs
Append-only: un trigger rifiuta `UPDATE`, `DELETE` e `TRUNCATE`. Contiene autore (id + copia dell'email), azione, entità, sintesi, dettagli JSONB, IP. Non deve contenere password, token o segreti.

### email_notifications
Coda persistente (transactional outbox) e registro degli invii. `idempotency_key` univoca impedisce di accodare due volte la stessa email. Stato PENDING/SENDING/SENT/FAILED/CANCELLED, tentativi, prossimo tentativo, lock del worker, ultimo errore, id del messaggio Mailjet.

## Conservazione dello storico

| Dato | Regola |
|---|---|
| Utenti | Mai cancellati; disattivazione tramite stato |
| Partite | Annullate, mai cancellate |
| Convocazioni | Rimozioni logiche + revisioni immutabili |
| Pagamenti | Richieste e quote saldate/annullate conservate |
| Audit log | Append-only a livello di database |
| Email | Conservate come registro; i payload con dati sensibili (link di reset) vanno ripuliti dopo l'invio |
| Refresh token / token monouso | Possono essere eliminati dopo la scadenza (pulizia periodica, prevista nella fase autenticazione) |

## Database locali

`backend/db/init-local.sql` crea il ruolo `squadra` e i database `squadra_dev` (sviluppo) e `squadra_test` (test di integrazione). Le tabelle vengono create da Flyway al primo avvio del backend o dei test.
