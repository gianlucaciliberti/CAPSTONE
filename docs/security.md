# Sicurezza

Stato: implementate la base (fase 1) e l'autenticazione con ruoli (fase 2). Le sezioni marcate *(fase N)* descrivono il design delle fasi successive.

## Base

- **Spring Security, deny by default**: sono pubblici solo `/actuator/health`, `/actuator/info`, `/api/v1/public/**`, gli endpoint di autenticazione elencati sotto e (solo se `SWAGGER_ENABLED=true`) Swagger. Ogni altra richiesta senza autenticazione riceve 401 in formato `application/problem+json`.
- **API stateless**: nessuna sessione HTTP, niente form login né HTTP Basic.
- **CORS restrittivo**: solo le origini in `CORS_ALLOWED_ORIGINS`, metodi e header espliciti (`Authorization`, `Content-Type`, `X-Requested-With`). Le richieste da altre origini ricevono 403.
- **Password**: `BCryptPasswordEncoder` con costo 12; regole minime (almeno 10 caratteri, una lettera e un numero, massimo 72 byte) applicate sia dal backend (`@StrongPassword`) sia dal frontend.
- **Errori**: messaggi generici, nessuno stack trace (`server.error.include-stacktrace=never`), eccezioni inattese solo nei log.
- **Audit log**: append-only garantito da trigger PostgreSQL. Registra registrazione, verifica email, approvazioni, rifiuti, sospensioni, riattivazioni, cambi di ruolo, creazione del primo Admin, cambio e reset della password.
- **Segreti**: solo variabili d'ambiente; `.env` è ignorato da Git; il profilo `prod` non ha valori di default per database, JWT e CORS (avvio bloccato se mancano).
- **Health check**: `show-details: never`, nessuna informazione interna esposta.
- **Header**: `Referrer-Policy: no-referrer` (backend e meta tag del frontend, così i token nei link di reset/verifica non finiscono nell'header Referer) più gli header predefiniti di Spring Security (`X-Content-Type-Options`, `X-Frame-Options: DENY`, `Cache-Control` sulle risposte protette; HSTS automatico sulle richieste HTTPS).

## Autenticazione

| Endpoint (`/api/v1/auth`) | Accesso | Limite per IP |
|---|---|---|
| `POST /register` | pubblico | 5/ora |
| `POST /verify-email` | pubblico | 20/15 min |
| `POST /resend-verification` | pubblico | 5/ora |
| `POST /login` | pubblico | 10/min |
| `POST /refresh` | cookie + header CSRF | 30/min |
| `POST /logout` | cookie + header CSRF | — |
| `POST /forgot-password` | pubblico | 5/ora |
| `POST /reset-password` | pubblico | 10/15 min |
| `GET /me` | autenticato | — |
| `POST /change-password` | autenticato | 10/15 min |

- **Access token JWT** firmato HS256 con `JWT_SECRET` (almeno 256 bit), durata 15 minuti, contiene solo l'id utente; inviato nell'header `Authorization: Bearer`. Il frontend lo tiene **solo in memoria** (mai in localStorage/sessionStorage).
- **Refresh token opaco** (256 bit casuali) in cookie `HttpOnly`, `Secure` (in produzione), `SameSite=Strict`, `Path=/api/v1/auth`, durata 14 giorni. Nel database solo l'hash SHA-256.
- **Rotazione** a ogni rinnovo; il riuso di un token già ruotato revoca l'intera sessione (probabile furto). Il frontend evita falsi positivi: nella stessa scheda i rinnovi contemporanei condividono una sola richiesta, tra schede diverse sono messi in fila con la Web Locks API.
- **Revoca**: logout (sessione corrente); cambio password, reset password, sospensione o rifiuto (tutte le sessioni). Il cambio password apre subito una nuova sessione per il dispositivo corrente.
- **Ruolo e stato riletti dal database a ogni richiesta**: sospensioni e cambi di ruolo hanno effetto immediato, anche con un access token già emesso.
- **CSRF**: `refresh` e `logout` si basano sul cookie `SameSite=Strict` e richiedono l'header `X-Requested-With: XMLHttpRequest` (un sito esterno non può aggiungerlo senza preflight CORS, rifiutata per le origini non ammesse); le altre API usano il Bearer token, non soggetto a CSRF.
- **Limitazione tentativi**: blocco dell'account per 15 minuti dopo 5 password errate; rate limiting per IP (in memoria, finestra fissa) con risposta 429 e header `Retry-After`. Soglie in `app.rate-limit` (`application.yml`), disattivabile con `RATE_LIMIT_ENABLED=false`. I contatori in memoria valgono per una singola istanza: con più istanze servirebbe un archivio condiviso (es. Redis).
- **Nessuna enumerazione degli utenti**: registrazione con email esistente, email inesistente al login, recupero password e reinvio della verifica rispondono sempre allo stesso modo; per le email inesistenti il login esegue comunque un confronto BCrypt. Lo stato dell'account (in attesa, sospeso, rifiutato) viene comunicato solo dopo una password corretta.
- **Registrazione e approvazione**: ruolo `PLAYER` e stato `PENDING` imposti dal server; il link di verifica email (valido 24 ore) parte subito. L'Admin può approvare solo utenti con email verificata; all'approvazione l'utente riceve un'email. Solo gli account `ACTIVE` ottengono i token.
- **Link monouso via email** (verifica e reset): token a 256 bit, nel database solo l'hash, consumato con lock di riga, un nuovo link invalida i precedenti, al massimo un'email ogni 2 minuti per utente. Il reset password azzera il blocco per tentativi falliti e considera verificata l'email. Il contenuto di queste email viene cancellato dalla coda dopo l'invio.
- **Pulizia**: ogni notte (03:30 Europe/Rome) vengono eliminati refresh token e token monouso scaduti da più di 7 giorni.
- **Primo Admin**: variabili `ADMIN_BOOTSTRAP_*` usate una sola volta, nessuna password predefinita nel codice (vedi README).
- **Utenti dimostrativi**: creati solo nel profilo `dev` con database vuoto; mai in produzione.

## Autorizzazione

- Controlli di ruolo su ogni endpoint (`@PreAuthorize`), mai basati su dati inviati dal client. Ruoli cumulativi: ADMIN include COACH, che include PLAYER.
- Le rotte protette del frontend (`RequireAuth`) servono solo all'interfaccia: i permessi veri sono verificati dal backend.
- I DTO di richiesta non contengono campi privilegiati (ruolo, stato, autore): questi sono sempre impostati dal server.
- Un Admin non può cambiare il proprio stato; l'ultimo Admin attivo non può essere declassato, sospeso o rifiutato (controllo con lock di riga sugli Admin attivi).
- *(fasi 3-6)* Accesso alle risorse altrui verificato nel servizio (es. un giocatore vede solo i propri pagamenti e modifica solo la propria disponibilità).

## HTTPS in produzione *(fase 8)*

Render termina TLS sul proprio proxy; il backend usa `server.forward-headers-strategy=framework` per riconoscere le richieste HTTPS (cookie `Secure`, HSTS, URL corretti).
