# Deployment su Render

Stato: **online** su https://squadra-app.onrender.com (backend `https://squadra-api.onrender.com`), creato dal blueprint [`render.yaml`](../render.yaml) con [`backend/Dockerfile`](../backend/Dockerfile).

## Verifiche sul servizio pubblicato (fase 8, parte 4)

| Verifica | Esito |
|---|---|
| Health check del backend | `UP` |
| Header di sicurezza del sito (CSP, X-Frame-Options, nosniff, Referrer-Policy, Permissions-Policy) e HSTS | presenti |
| Rewrite `/api/*` (stessa origine) e routing di React sulle pagine interne | funzionanti |
| Cache: `no-cache` su `/` e `sw.js`; `immutable` sugli asset | corrette |
| App caricata in un browser reale con la CSP attiva | nessuna violazione |
| Login del primo Admin creato da `ADMIN_BOOTSTRAP_*` (variabili poi rimosse) | funzionante |
| Email reali con Mailjet (conferma email, account approvato) | consegnate |
| Rate limiting con `X-Forwarded-For` falsificato, diretto e attraverso il sito | blocca all'11° tentativo (vedi `security.md`) |

## Se il deploy del backend fallisce con "Port scan timeout"

Significa che il backend non ha aperto la porta entro il tempo concesso da Render. Sul piano gratuito, con poca CPU, l'avvio di Spring Boot può essere lento e capita occasionalmente (è successo una volta in fase 8): un **Manual Deploy** dal pannello del servizio di solito basta. Se si ripete spesso, controllare nei *Logs* che non ci siano errori di avvio (`ERROR`, `Caused by`) e nella scheda *Events* eventuali esaurimenti di memoria.

> Piani, prezzi e limiti di Render cambiano nel tempo: verificarli su https://render.com/pricing prima di scegliere. Il blueprint usa i piani gratuiti (`plan: free`); senza questa indicazione Render userebbe un piano a pagamento.

## Servizi (render.yaml)

| Servizio | Tipo Render | Dettagli |
|---|---|---|
| `squadra-db` | PostgreSQL 18 (`free`, Francoforte) | Raggiungibile solo dalla rete privata di Render (`ipAllowList: []`) |
| `squadra-api` | Web Service, runtime **Docker** | `backend/Dockerfile`; health check `/actuator/health`; deploy automatico a ogni push su `main` |
| `squadra-app` | Static Site | root `frontend`, build `npm ci && npm run build`, pubblica `dist`; rewrite e header (sotto) |

Render non ha un runtime Java nativo: il backend gira in un container. Il `Dockerfile` compila con il JDK 25 e il Maven wrapper, poi crea un'immagine con il solo JRE 25, eseguita da un utente non root, con la JVM limitata al 75% della memoria del container. **I test non girano durante la build** (servirebbe un PostgreSQL): eseguire `./mvnw test` e `npm test` in locale prima di ogni push su `main`, perché ogni push viene pubblicato.

## Variabili d'ambiente del backend

Impostate dal blueprint, salvo dove indicato.

| Variabile | Valore |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` (anche nel `Dockerfile`) |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | dal database `squadra-db` (indirizzo interno); `application-prod.yml` compone l'URL JDBC. In alternativa si può impostare un `DB_URL` completo |
| `JWT_SECRET` | generato da Render (256 bit casuali, Base64) |
| `MAILJET_WEBHOOK_SECRET` | generato da Render; serve per l'URL del webhook Mailjet (vedi `email-setup.md`) |
| `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` | `https://squadra-app.onrender.com` |
| `MAIL_MODE` | `MAILJET` (obbligatoria in produzione) |
| `MAIL_FROM_NAME` | `Gestionale Squadra` |
| `MAIL_FROM_EMAIL`, `MAILJET_API_KEY`, `MAILJET_API_SECRET` | **richieste alla creazione del blueprint** (`sync: false`, mai scritte nel repository) |
| `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD`, `ADMIN_BOOTSTRAP_FIRST_NAME`, `ADMIN_BOOTSTRAP_LAST_NAME` | **richieste alla creazione**; servono solo al primo avvio, poi vanno **cancellate** dal pannello del servizio (vedi README) |
| `REVIEWER_EMAIL`, `REVIEWER_PASSWORD` (facoltative `REVIEWER_FIRST_NAME`, `REVIEWER_LAST_NAME`) | facoltative: account Admin del valutatore esterno con email fittizia, creato al riavvio se non esiste (vedi README) |
| `OPENAI_ENABLED` | `false` |
| `PORT` | impostata da Render; il backend la legge automaticamente |

## Regole del sito statico

- Rewrite `/api/*` → `https://squadra-api.onrender.com/api/*`: le API stanno sulla stessa origine del sito, così il cookie del refresh token resta first-party (`SameSite=Strict`) e non servono richieste cross-origin.
- Rewrite `/*` → `/index.html` (routing lato client di React Router). Render non applica le regole ai file che esistono: `favicon.svg`, `sw.js` e `assets/*` vengono serviti direttamente.
- Header di sicurezza su tutti i percorsi (`/*`), uguali a [`frontend/security-headers.js`](../frontend/security-headers.js): `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy` (dettagli in `security.md`). `securityHeaders.test.js` controlla che `render.yaml` e `security-headers.js` restino allineati. In locale si provano con `npm run build && npm run preview`.
- `Cache-Control: no-cache` per `/`, `/index.html` e `/sw.js`: il browser verifica sempre se esiste una nuova versione. I file in `/assets/*` hanno l'hash nel nome e si tengono in cache per un anno (`immutable`).

## Nomi dei servizi e URL

I nomi diventano gli indirizzi pubblici (`https://<nome>.onrender.com`). Se `squadra-api` o `squadra-app` fossero già occupati, Render aggiunge un suffisso al nome: in quel caso aggiornare in `render.yaml` i tre URL marcati con il commento "URL" (`FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`, destinazione della rewrite `/api/*`), fare commit e push.

## Limiti del piano gratuito da tenere presenti

Da verificare sulla documentazione Render al momento della scelta:

- **Sospensione per inattività del web service**: mentre il backend è sospeso non girano né il worker della coda email né il job dei promemoria delle 9:00. Le email restano in coda e partono al risveglio; un promemoria saltato viene recuperato al primo job successivo (le chiavi di idempotenza evitano doppioni). La prima richiesta dopo la sospensione è lenta (avvio del container).
- **Durata e backup del database gratuito**: controllare se il database gratuito ha una scadenza e se include backup. Per un uso reale della squadra conviene un piano a pagamento con backup.

## Vecchio service worker

L'app non è più installabile (PWA rimossa, da reintrodurre in futuro) e non registra service worker. I browser che avevano aperto il sito quando lo era scaricano ancora `/sw.js` al controllo degli aggiornamenti: ora quel file cancella le cache dell'app, si rimuove e ricarica la pagina. Va lasciato pubblicato finché quei dispositivi non sono stati aggiornati.

## Health check

`GET /actuator/health` (pubblico, senza dettagli), impostato come `healthCheckPath`: Render considera il deploy riuscito solo quando risponde `UP`.

## Backup e ripristino (indicazioni)

Il database accetta connessioni solo dalla rete privata di Render. Per un backup dal proprio PC aggiungere temporaneamente il proprio IP nella sezione *Access Control* del database (e rimuoverlo subito dopo), poi:

- Backup: `pg_dump --format=custom --no-owner -d "<external-connection-string>" -f backup.dump`
- Ripristino: `pg_restore --no-owner --clean --if-exists -d "<connection-string>" backup.dump`
- Conservare i dump fuori dal repository (già ignorati da `.gitignore`) e cifrati: contengono dati personali.
- Provare periodicamente il ripristino su un database di prova.
