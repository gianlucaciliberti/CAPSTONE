# Sicurezza

Stato: la **fase 1** implementa la base (tutto chiuso per default, CORS restrittivo, BCrypt, errori uniformi, audit log append-only). Le sezioni marcate *(fase N)* descrivono il design che verrà implementato nelle fasi successive.

## Implementato in fase 1

- **Spring Security, deny by default**: sono pubblici solo `/actuator/health`, `/actuator/info`, `/api/v1/public/**` e (solo se `SWAGGER_ENABLED=true`) Swagger. Ogni altra richiesta senza autenticazione riceve 401 in formato `application/problem+json`.
- **API stateless**: nessuna sessione HTTP, niente form login né HTTP Basic.
- **CORS restrittivo**: solo le origini in `CORS_ALLOWED_ORIGINS`, metodi e header espliciti. Le richieste da altre origini ricevono 403.
- **Password**: `BCryptPasswordEncoder` con costo 12.
- **Errori**: messaggi generici, nessuno stack trace (`server.error.include-stacktrace=never`), eccezioni inattese solo nei log.
- **Audit log**: append-only garantito da trigger PostgreSQL.
- **Segreti**: solo variabili d'ambiente; `.env` è ignorato da Git; il profilo `prod` non ha valori di default per database, JWT e CORS (avvio bloccato se mancano).
- **Health check**: `show-details: never`, nessuna informazione interna esposta.
- **Header**: `Referrer-Policy: no-referrer` più gli header predefiniti di Spring Security (`X-Content-Type-Options`, `X-Frame-Options: DENY`, `Cache-Control` sulle risposte protette; HSTS automatico sulle richieste HTTPS).

## Autenticazione *(fase 2)*

- **Access token JWT** firmato HS256 con `JWT_SECRET`, durata 15 minuti, inviato nell'header `Authorization: Bearer`. Il frontend lo tiene **solo in memoria** (mai in localStorage/sessionStorage).
- **Refresh token opaco** (256 bit casuali) in cookie `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth`. Nel database solo l'hash SHA-256.
- **Rotazione** a ogni rinnovo; il riuso di un token già ruotato revoca l'intera famiglia (sessione compromessa).
- **Revoca**: logout (sessione corrente), cambio/reset password, sospensione o rifiuto dell'account (tutte le sessioni).
- **Stato account verificato a ogni richiesta**: un utente sospeso perde l'accesso entro la durata dell'access token; il rinnovo è rifiutato subito.
- **CSRF**: `POST /auth/refresh` e `POST /auth/logout` si basano sul cookie `SameSite=Strict` e richiedono l'header `X-Requested-With: XMLHttpRequest` (un sito esterno non può aggiungerlo senza preflight CORS, rifiutata per le origini non ammesse); le altre API usano il Bearer token, non soggetto a CSRF.
- **Limitazione tentativi**: blocco dell'account per 15 minuti dopo 5 password errate; rate limiting per IP (in memoria, finestra fissa) su `POST` login (10/min), registrazione (5/ora) e refresh (30/min), con risposta 429 e header `Retry-After`. Soglie in `app.rate-limit` (`application.yml`), disattivabile con `RATE_LIMIT_ENABLED=false`. I contatori in memoria valgono per una singola istanza: con più istanze servirebbe un archivio condiviso (es. Redis).
- **Registrazione**: ruolo `PLAYER` e stato `PENDING` imposti dal server; accesso alle funzioni protette solo dopo approvazione dell'Admin e verifica email.
- **Recupero password**: `POST /auth/forgot-password` risponde sempre allo stesso modo (niente enumerazione utenti); l'email parte solo per account esistenti non sospesi/rifiutati e al massimo una volta ogni 2 minuti. Il link contiene un token monouso a 256 bit valido 30 minuti (nel database solo l'hash; un nuovo link invalida i precedenti). `POST /auth/reset-password` consuma il token con lock di riga, imposta la nuova password, azzera il blocco per tentativi falliti, chiude tutte le sessioni e registra l'operazione nell'audit log. Il contenuto dell'email viene cancellato dalla coda dopo l'invio. La pagina frontend che riceve il link dovrà usare `Referrer-Policy: no-referrer` per non esporre il token ad altri siti.
- **Primo Admin**: procedura dedicata senza password predefinite (documentata nel README in fase 2).

## Autorizzazione *(fasi 2-6)*

- Controlli di ruolo su ogni endpoint (`@PreAuthorize`), mai basati su dati inviati dal client.
- Accesso alle risorse altrui verificato nel servizio (es. un giocatore vede solo i propri pagamenti e modifica solo la propria disponibilità).
- I DTO di richiesta non contengono campi privilegiati (ruolo, autore, stato del pagamento): questi sono sempre impostati dal server.
- L'ultimo Admin attivo non può essere declassato, sospeso o rifiutato.

## HTTPS in produzione *(fase 8)*

Render termina TLS sul proprio proxy; il backend usa `server.forward-headers-strategy=framework` per riconoscere le richieste HTTPS (cookie `Secure`, HSTS, URL corretti).
