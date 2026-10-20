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
| 2. Autenticazione e ruoli | ✅ |
| 3. Calendario e disponibilità | in corso (parte 1 di 2 completata: calendario e partite) |
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
- Swagger UI (solo dev): http://localhost:8080/swagger-ui.html (pulsante *Authorize*: incollare l'`accessToken` restituito da `POST /api/v1/auth/login`)

**Utenti dimostrativi (solo profilo `dev`)**: se il database `squadra_dev` è vuoto, al primo avvio vengono creati `admin@demo.local`, `coach@demo.local`, quattro giocatori attivi (`paolo@`, `luca@`, `marco@`, `davide@demo.local`) e uno in attesa (`simone@demo.local`), tutti con password `Calcetto2026`, e cinque partite intorno alla data odierna. In produzione (profilo `prod`) non vengono mai creati.

**Email in sviluppo**: con `MAIL_MODE=LOG` nessuna email viene inviata; il testo completo (con i link di verifica e di reset password) compare nel log del backend a livello DEBUG.

> **Windows**: se nelle variabili d'ambiente di sistema esistono già `DB_PASSWORD` o `PORT` (es. impostate per altri progetti), hanno la precedenza sul file `.env`. Rimuoverle o avviare il backend con i valori corretti.

### Creazione del primo Admin

Nel codice non esistono utenti o password predefiniti. Il primo amministratore si crea così (in locale e su Render):

1. Impostare le variabili d'ambiente (in locale nel file `.env`, su Render nel pannello *Environment* del servizio):
   ```
   ADMIN_BOOTSTRAP_EMAIL=tua.email@example.com
   ADMIN_BOOTSTRAP_PASSWORD=<password robusta: almeno 10 caratteri, lettere e numeri>
   ADMIN_BOOTSTRAP_FIRST_NAME=Nome
   ADMIN_BOOTSTRAP_LAST_NAME=Cognome
   ```
2. Avviare (o riavviare) il backend. Se nel database non esiste alcun Admin, ne viene creato uno attivo e il log mostra "Primo Admin creato". L'operazione è registrata nell'audit log.
3. **Rimuovere subito** le variabili `ADMIN_BOOTSTRAP_*` e riavviare. Finché restano impostate non succede nulla (esiste già un Admin), ma la password non deve rimanere nell'ambiente.

Se le variabili contengono dati non validi (es. password debole) il backend non si avvia e il log indica il problema. Gli Admin successivi si nominano dall'interfaccia, cambiando il ruolo di un utente approvato.

## 5. Avvio del frontend

```bash
cd frontend
npm install
npm run dev
```

Aprire http://localhost:5173. Il dev server inoltra le chiamate `/api` al backend su `localhost:8080` (modificabile con `VITE_DEV_BACKEND_URL` in `frontend/.env`).

Pagine disponibili: calendario mensile, dettaglio partita (con creazione, modifica e annullamento per l'Admin), prossime partite in home, login, registrazione, conferma email, recupero e reimpostazione password, profilo (cambio password) e, per l'Admin, gestione utenti (approvazione, rifiuto, sospensione, riattivazione, ruoli).

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
