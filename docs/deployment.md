# Deployment su Render

Stato: il progetto è già predisposto (porta da `PORT`, profilo `prod`, health check, header di proxy). Blueprint (`render.yaml`), verifica delle rewrite e procedura completa vengono finalizzati nella **fase 8**.

> Piani, prezzi e limiti di Render cambiano nel tempo: verificarli su https://render.com/pricing e nella documentazione ufficiale prima di scegliere. In particolare va controllato se il piano scelto per PostgreSQL include backup automatici e per quanto tempo, e se il piano gratuito del web service va in sospensione dopo inattività.

## Servizi

| Servizio | Tipo Render | Note |
|---|---|---|
| Frontend | Static Site | root `frontend`, build `npm ci && npm run build`, publish `dist` |
| Backend | Web Service | root `backend`, runtime Docker o Java (definito in fase 8) |
| Database | PostgreSQL | stessa versione major usata in sviluppo |

## Regole del sito statico

- Rewrite `/api/*` → `https://<backend>.onrender.com/api/:splat` (API sulla stessa origine del sito).
- Rewrite `/*` → `/index.html` (routing lato client di React Router). I file reali (`/sw.js`, `/manifest.webmanifest`, `/offline.html`, `/icons/*`, `/assets/*`) vengono serviti prima della rewrite.
- Header di sicurezza su tutti i percorsi (`/*`), con i valori di [`frontend/security-headers.js`](../frontend/security-headers.js): `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy` (dettagli in `security.md`). Prima del deploy si possono provare in locale con `npm run build && npm run preview`, che li applica.
- Header `Cache-Control: no-cache` per `/sw.js`, `/index.html` e `/manifest.webmanifest`: il browser deve sempre verificare se esiste una nuova versione, altrimenti gli aggiornamenti dell'app arrivano in ritardo. I file in `/assets/*` hanno l'hash nel nome e possono essere messi in cache a lungo (`public, max-age=31536000, immutable`).

## PWA

- Il service worker funziona solo su HTTPS (fornito da Render) o su `localhost`; viene registrato solo nella build di produzione. Per provarlo in locale: `npm run build && npm run preview`.
- Il plugin `service-worker-manifest` di `vite.config.js` scrive in `dist/sw.js` l'elenco dei file della build e una versione calcolata dai loro nomi: a ogni deploy con codice diverso gli utenti vedono l'avviso "È disponibile una nuova versione" e aggiornano con un clic.
- Le API non vengono mai salvate in cache dal service worker (dati personali e di autenticazione).

## Variabili d'ambiente del backend

| Variabile | Esempio / note |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:postgresql://<host>:5432/<db>` — Render fornisce un URL `postgresql://...` che va convertito nel formato JDBC; usare l'*internal* hostname |
| `DB_USERNAME`, `DB_PASSWORD` | dal pannello del database Render |
| `FRONTEND_URL` | `https://<frontend>.onrender.com` |
| `CORS_ALLOWED_ORIGINS` | `https://<frontend>.onrender.com` |
| `JWT_SECRET` | segreto Base64 ≥ 256 bit, generato ad hoc |
| `MAIL_MODE` | obbligatoria: `MAILJET` (con `LOG` le email non partono) |
| `MAIL_FROM_EMAIL`, `MAIL_FROM_NAME`, `MAILJET_*` | vedi `email-setup.md` |
| `OPENAI_ENABLED` | `false` |

## Health check

`GET /actuator/health` (pubblico, senza dettagli). Da impostare come *Health Check Path* del web service.

## Backup e ripristino (indicazioni)

- Backup manuale: `pg_dump --format=custom --no-owner -d "<external-connection-string>" -f backup.dump`
- Ripristino: `pg_restore --no-owner --clean --if-exists -d "<connection-string>" backup.dump`
- Conservare i dump fuori dal repository (sono già ignorati da `.gitignore`) e cifrati se contengono dati personali.
- Provare periodicamente il ripristino su un database di prova.
