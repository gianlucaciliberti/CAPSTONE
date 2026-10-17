# Gestionale Squadra

Applicazione web per gestire una squadra di calcio amatoriale: utenti e ruoli, calendario, disponibilità, convocazioni con notifiche email, risultati e tabellini, pagamenti manuali, audit log.

- **Frontend**: React 19 + Vite 8 + Bootstrap 5 + React Router 8 (JavaScript) — `frontend/`
- **Backend**: Java 25 + Spring Boot 4.1 (Spring Security, JWT, Spring Data JPA, Flyway, Bean Validation) — `backend/`
- **Database**: PostgreSQL (gestibile con pgAdmin)
- **Integrazioni**: Mailjet (email), OpenAI (predisposto, disattivato)
- **Deployment**: Render

Documentazione tecnica in [`docs/`](docs): [architettura](docs/architecture.md), [database](docs/database.md), [sicurezza](docs/security.md), [email](docs/email-setup.md), [deployment](docs/deployment.md).

## Stato del progetto

| Fase | Stato |
|---|---|
| 1. Struttura, configurazione, database e migrazioni | ✅ |
| 2. Autenticazione e ruoli | da fare |
| 3. Calendario e disponibilità | da fare |
| 4. Convocazioni e email | da fare |
| 5. Risultati e tabellini | da fare |
| 6. Pagamenti | da fare |
| 7. Dashboard responsive e PWA | da fare |
| 8. Test, sicurezza e deployment | da fare |

## Prerequisiti

- **JDK 25** (es. Eclipse Temurin). Maven non serve: si usa il wrapper `mvnw` incluso.
- **Node.js ≥ 22.22** e npm.
- **PostgreSQL** (sviluppato con la versione 18) e, facoltativamente, **pgAdmin**.
- IntelliJ IDEA (consigliato): aprire la cartella `CAPSTONE`, poi importare `backend/pom.xml` come progetto Maven.

## 1. Database locale

1. Aprire pgAdmin, connettersi al server locale come `postgres` e aprire il *Query Tool* sul database `postgres`.
2. Aprire [`backend/db/init-local.sql`](backend/db/init-local.sql), sostituire `CAMBIA_QUESTA_PASSWORD` con una password a scelta ed eseguire lo script (F5).
   Vengono creati il ruolo `squadra` e due database separati: `squadra_dev` (sviluppo) e `squadra_test` (test automatici).

In alternativa da terminale:

```bash
psql -U postgres -h localhost -f backend/db/init-local.sql
```

## 2. Variabili d'ambiente

```bash
cp .env.example .env                    # configurazione backend
cp frontend/.env.example frontend/.env  # configurazione frontend (facoltativa)
```

Compilare almeno `DB_PASSWORD` nel file `.env`. Il file è ignorato da Git. Il backend lo legge automaticamente all'avvio (dalla root del repository o da `backend/`). L'elenco completo e commentato delle variabili è in [`.env.example`](.env.example).

Senza credenziali esterne lo sviluppo locale funziona comunque: `MAIL_MODE=LOG` scrive le email nei log invece di inviarle, `OPENAI_ENABLED=false` disattiva OpenAI.

## 3. Migrazioni

Le migrazioni Flyway (`backend/src/main/resources/db/migration`) vengono applicate **automaticamente all'avvio** del backend. Hibernate verifica poi che le entità corrispondano allo schema (`ddl-auto=validate`) e non modifica mai le tabelle. Per cambiare lo schema si aggiunge un nuovo file `V<n>__descrizione.sql`; i file già applicati non vanno modificati.

## 4. Avvio del backend

```bash
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Da IntelliJ: eseguire `GestionaleApplication` (working directory `backend`). Il profilo predefinito è `dev`.

- API: http://localhost:8080/api/v1
- Health check: http://localhost:8080/actuator/health
- Swagger UI (solo dev): http://localhost:8080/swagger-ui.html

## 5. Avvio del frontend

```bash
cd frontend
npm install
npm run dev
```

Aprire http://localhost:5173. Il dev server inoltra le chiamate `/api` al backend su `localhost:8080`.

## 6. Test

```bash
# Backend: test unitari e di integrazione (usano il database squadra_test)
cd backend
./mvnw test

# Frontend: lint, test e build di produzione
cd frontend
npm run lint
npm test
npm run build
```

I test di integrazione del backend richiedono PostgreSQL attivo e il database `squadra_test` (creato da `init-local.sql`). Usano `DB_USERNAME`/`DB_PASSWORD` del file `.env`, oppure `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`.

### Nota: errore "PKIX path building failed" con Maven

Se un antivirus o un proxy aziendale ispeziona il traffico HTTPS, la JVM può non riconoscerne il certificato e Maven non riesce a scaricare le dipendenze. Su Windows si può far usare alla JVM il trust store di sistema, solo per la sessione corrente:

```powershell
$env:MAVEN_OPTS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT'
.\mvnw.cmd test
```

Non aggiungere questa opzione ai file del progetto: vale solo per Windows e romperebbe la build su Render (Linux).

## 7. Deployment

Vedi [docs/deployment.md](docs/deployment.md) (completato nella fase 8).
