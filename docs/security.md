# Sicurezza

Stato: completo per backend e frontend (revisione della fase 8, parti 2 e 4), verificato anche sul servizio pubblicato su Render.

## Base

- **Spring Security, deny by default**: sono pubblici solo `/actuator/health`, `/actuator/info`, `/api/v1/public/**`, gli endpoint di autenticazione elencati sotto e (solo se `SWAGGER_ENABLED=true`) Swagger. Ogni altra richiesta senza autenticazione riceve 401 in formato `application/problem+json`.
- **API stateless**: nessuna sessione HTTP, niente form login né HTTP Basic.
- **CORS restrittivo**: solo le origini in `CORS_ALLOWED_ORIGINS`, metodi e header espliciti (`Authorization`, `Content-Type`, `X-Requested-With`). Le richieste da altre origini ricevono 403.
- **Password**: `BCryptPasswordEncoder` con costo 12; regole minime (almeno 10 caratteri, una lettera e un numero, massimo 72 byte) applicate sia dal backend (`@StrongPassword`) sia dal frontend.
- **Errori**: messaggi generici, nessuno stack trace (`server.error.include-stacktrace=never`), eccezioni inattese solo nei log.
- **Audit log**: append-only garantito da trigger PostgreSQL. Registra registrazione, verifica email, approvazioni, rifiuti, sospensioni, riattivazioni, cambi di ruolo, creazione del primo Admin, cambio e reset della password.
- **Segreti**: solo variabili d'ambiente; `.env` è ignorato da Git; il profilo `prod` non ha valori di default per database, JWT e CORS (avvio bloccato se mancano).
- **Health check**: `show-details: never`, nessuna informazione interna esposta.
- **Header del backend**: `Referrer-Policy: no-referrer` (così i token nei link di reset/verifica non finiscono nell'header Referer), `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'` sulle API (rispondono solo JSON: nessuna risorsa caricabile, nessun incorporamento in frame), `Permissions-Policy` che disattiva fotocamera, microfono, geolocalizzazione, pagamenti e USB, più gli header predefiniti di Spring Security (`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-cache, no-store` sulle risposte; HSTS automatico sulle richieste HTTPS). Verificati da `PublicEndpointsIntegrationTest`, anche sulle risposte 401.

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
- **Disponibilità**: gli endpoint "/availability/me" agiscono sempre e solo sull'utente autenticato (nessun id nel percorso); modificabili finché la partita è programmata e futura e la convocazione non è pubblicata. Il riepilogo della squadra è riservato ad Allenatori e Admin; la correzione delle disponibilità altrui solo all'Admin, anche dopo la pubblicazione, con audit (AVAILABILITY_OVERRIDDEN).
- **Partite**: lettura per tutti gli utenti attivi; creazione, modifica e annullamento solo Admin, con controllo di versione (409 in caso di modifiche concorrenti) e audit.
- **Convocazioni**: gestione (bozza, pubblicazione, modifica, ritiro, storico) solo per Allenatori e Admin, con controllo di versione e audit (CALLUP_PUBLISHED, CALLUP_UPDATED, CALLUP_WITHDRAWN). Le bozze non sono visibili ai giocatori; i giocatori non vedono le disponibilità altrui. Convocare utenti non disponibili o senza risposta richiede una conferma esplicita (`confirmWarnings`), verificata anche dal server. Revisioni immutabili e rimozioni logiche: nessuno storico viene cancellato.
- **Email**: stato degli invii consultabile da Allenatori e Admin, senza contenuto delle email; errori tecnici del provider solo per l'Admin; retry e annullamento solo Admin, con audit (EMAIL_RETRIED, EMAIL_CANCELLED). Il webhook Mailjet è pubblico per Spring Security ma protetto da autenticazione Basic (password = `MAILJET_WEBHOOK_SECRET`, confronto a tempo costante), disattivato (404) se il segreto non è configurato e soggetto a rate limiting.
- **Dashboard** (`GET /api/v1/dashboard`): sempre sull'utente autenticato (nessun id nel percorso). I dati dello staff sono calcolati solo in base al ruolo letto dal database: Allenatori e Admin vedono partite senza convocazione e numero di email fallite; solo l'Admin vede partite da chiudere, registrazioni da approvare e totale da incassare. Nessun dato personale di altri utenti: solo conteggi e partite.
- **Risultati e tabellini**: lettura per tutti gli utenti attivi; risultato ufficiale ed eventi solo Admin, per partite già iniziate e non annullate, con controllo di versione e audit di ogni modifica con i valori precedenti (MATCH_RESULT_RECORDED, MATCH_EVENT_CREATED/UPDATED/DELETED). Gli avversari non possono essere collegati a un account. Le incoerenze tra risultato ed eventi sono segnalate, non bloccate. Storico dei risultati e statistiche dei giocatori in sola lettura per tutti gli utenti attivi.
- **Pagamenti**: ogni utente vede solo le proprie quote (`GET /api/v1/payments/me`, sempre sull'utente autenticato, senza la nota interna dell'Admin); creazione, modifica, annullamento, assegnazione, conferma, esonero e riapertura delle quote solo Admin, con controllo di versione, lock di riga sulla richiesta e audit (PAYMENT_REQUEST_CREATED/UPDATED/CANCELLED, PAYMENT_ASSIGNED, PAYMENT_CONFIRMED, PAYMENT_WAIVED, PAYMENT_REOPENED, PAYMENT_REMINDERS_SENT). Promemoria manuali solo Admin, al massimo uno al giorno per quota; le email non contengono la nota interna. Destinatari solo tra gli utenti attivi; data di pagamento mai nel futuro. Nessuna richiesta o quota viene cancellata.

## Sito statico (frontend)

Gli header del sito sono definiti in un unico file, [`frontend/security-headers.js`](../frontend/security-headers.js): li usa `npm run preview` per provare la build in locale e vanno impostati identici sul sito statico di Render (vedi `deployment.md`).

- **Content-Security-Policy** senza `unsafe-inline` né `unsafe-eval`: script, stili, font, connessioni, manifest e service worker solo dalla stessa origine (le API lo sono grazie alla rewrite `/api/*`); immagini anche `data:` per le icone SVG incorporate nel CSS di Bootstrap; `object-src 'none'`, `base-uri 'self'`, `form-action 'self'`, `frame-ancestors 'none'` (protezione dal clickjacking).
- Nessuno script, stile o gestore di eventi inline in `index.html` e `offline.html` (lo stile della pagina offline è in `offline.css`): lo verifica `securityHeaders.test.js`. La build è stata provata in un browser reale con la CSP attiva, senza violazioni.
- Gli stili impostati da React (`style={{...}}`) passano dal DOM e non sono bloccati dalla CSP.
- Inoltre: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Permissions-Policy` come il backend.
- Il frontend non usa `innerHTML` né `eval`; tutto il testo passa dall'escape automatico di React. Il ritorno alla pagina richiesta dopo il login usa solo percorsi interni dell'app (nessun redirect verso siti esterni).

## Configurazione di produzione

- Il profilo `prod` non ha valori predefiniti per database, JWT, CORS, URL del frontend e **modalità email** (`MAIL_MODE`): se una variabile manca il backend non si avvia, invece di partire con un comportamento sbagliato (es. email scritte solo nei log).
- Swagger UI e `/v3/api-docs` sono disattivati salvo `SWAGGER_ENABLED=true` (da non impostare in produzione).
- Errori senza stack trace né messaggi interni; actuator espone solo `health` (senza dettagli) e `info`.
- Il contenuto delle email (con i link) compare nei log solo a livello DEBUG, attivo nel profilo `dev`.

## HTTPS e IP dei client dietro i proxy

Render termina TLS sul proprio proxy e la richiesta attraversa anche Cloudflare. IP del client e schema `https` li ricava [`ClientIpFilter`](../backend/src/main/java/it/squadra/gestionale/security/ClientIpFilter.java), il primo filtro della catena; il filtro standard di Spring è disattivato (`server.forward-headers-strategy=none`).

**Problema trovato in produzione (fase 8, parte 4).** Render e Cloudflare **aggiungono** il proprio indirizzo in fondo a `X-Forwarded-For` senza cancellare quelli inviati dal client. Il filtro standard di Spring prendeva il primo valore: inviando un `X-Forwarded-For` falso diverso a ogni richiesta, il limite di 10 tentativi di login al minuto non scattava mai (provato sul servizio reale).

Header reali misurati sul servizio (con una diagnostica temporanea, poi rimossa):

| Percorso | `X-Forwarded-For` ricevuto dal backend |
|---|---|
| Diretto al backend | `[valori del client], <IP client>, 172.x (Cloudflare), 10.x (Render)` |
| Dal sito (rewrite `/api/*`) | `[valori del client], <IP client>, 162.158.x, 162.158.x (Cloudflare), 74.220.48.x (sito statico Render), 172.x (Cloudflare), 10.x` |

`CF-Connecting-IP` non è utilizzabile: passando dal sito contiene l'indirizzo del sito statico, uguale per tutti gli utenti.

**Soluzione.** L'elenco si legge **da destra** saltando gli indirizzi dell'infrastruttura (loopback e reti private, intervalli ufficiali di Cloudflare, 74.220.48.0/20 assegnato a Render): il primo indirizzo rimasto è il client reale, perché i valori falsi stanno sempre alla sua sinistra. Gli header vengono considerati solo se la connessione arriva da un indirizzo fidato. `X-Forwarded-Proto: https` rende la richiesta sicura (cookie `Secure`, HSTS). I test di `SecurityComponentsTest` usano le catene misurate. Gli intervalli di Cloudflare e Render vanno aggiornati se i fornitori li cambiano (elenco in `ClientIpFilter`).

## Dipendenze e segreti

- **Segreti**: nessun segreto nel repository né nella sua storia (`.env` mai committato; l'unico valore simile a un segreto è la chiave fittizia dei test in `application-test.yml`). Verificato con una ricerca nei file tracciati e nella storia git.
- **Dipendenze frontend**: `npm audit` (incluso in npm, nessun plugin) senza vulnerabilità note alla data della revisione. Da ripetere prima di ogni rilascio.
- **Dipendenze backend**: versioni gestite da Spring Boot 4.1.1, l'ultima stabile alla data della revisione. Per restare aggiornati basta alzare la versione del parent nel `pom.xml` alle nuove patch (4.1.x), che includono le correzioni di sicurezza delle librerie gestite.
