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
- Rewrite `/*` → `/index.html` (routing lato client di React Router).

## Variabili d'ambiente del backend

| Variabile | Esempio / note |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:postgresql://<host>:5432/<db>` — Render fornisce un URL `postgresql://...` che va convertito nel formato JDBC; usare l'*internal* hostname |
| `DB_USERNAME`, `DB_PASSWORD` | dal pannello del database Render |
| `FRONTEND_URL` | `https://<frontend>.onrender.com` |
| `CORS_ALLOWED_ORIGINS` | `https://<frontend>.onrender.com` |
| `JWT_SECRET` | segreto Base64 ≥ 256 bit, generato ad hoc |
| `MAIL_*`, `MAILJET_*` | vedi `email-setup.md` |
| `OPENAI_ENABLED` | `false` |

## Health check

`GET /actuator/health` (pubblico, senza dettagli). Da impostare come *Health Check Path* del web service.

## Backup e ripristino (indicazioni)

- Backup manuale: `pg_dump --format=custom --no-owner -d "<external-connection-string>" -f backup.dump`
- Ripristino: `pg_restore --no-owner --clean --if-exists -d "<connection-string>" backup.dump`
- Conservare i dump fuori dal repository (sono già ignorati da `.gitignore`) e cifrati se contengono dati personali.
- Provare periodicamente il ripristino su un database di prova.
