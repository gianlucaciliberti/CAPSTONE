# Gestionale Squadra · Valhalla FC

Applicazione web per gestire una squadra di calcetto amatoriale: calendario, disponibilità, convocazioni con avvisi email, risultati e tabellini, statistiche, pagamenti delle quote e gestione degli utenti con tre ruoli (Giocatore, Allenatore, Admin).

Online su **https://squadra-app.onrender.com**.

- `backend/`: Java 25 + Spring Boot 4.1, PostgreSQL
- `frontend/`: React 19 + Vite + Bootstrap 5.3
- `docs/`: [architettura](docs/architecture.md), [database](docs/database.md), [sicurezza](docs/security.md), [email](docs/email-setup.md), [deployment](docs/deployment.md); sistema grafico in [DESIGN.md](DESIGN.md)

## Accesso per la valutazione

Account Admin per provare tutte le funzioni, online e in locale:

| Email | Password |
|---|---|
| `ospite@demo.local` | `Calcetto2026` |

L'account può modificare i dati e far partire email ai giocatori: usalo solo per la valutazione. Online il primo accesso può richiedere circa un minuto, il tempo che il servizio gratuito di Render si risvegli.

## Avvio in locale

**Serve:** JDK 25 (Maven no, c'è il wrapper `mvnw`), Node.js ≥ 22.22, PostgreSQL 18. IntelliJ IDEA è consigliato: apri la cartella `CAPSTONE` e importa `backend/pom.xml` come progetto Maven.

**1. Database.** Apri [`backend/db/init-local.sql`](backend/db/init-local.sql), sostituisci `CAMBIA_QUESTA_PASSWORD` ed eseguilo come utente `postgres`, dal Query Tool di pgAdmin o da terminale:

```bash
psql -U postgres -h localhost -f backend/db/init-local.sql
```

Lo script crea l'utente `squadra` e i database `squadra_dev` (sviluppo) e `squadra_test` (test). Le tabelle le crea Flyway al primo avvio del backend.

**2. Configurazione.**

```bash
cp .env.example .env
```

Nel `.env` basta `DB_PASSWORD`; tutte le altre variabili sono spiegate in [`.env.example`](.env.example). Il backend legge il file da solo. Senza credenziali esterne funziona tutto lo stesso: con `MAIL_MODE=LOG` le email finiscono nel log invece di partire.

**3. Backend.**

```bash
cd backend
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
```

Da IntelliJ basta eseguire `GestionaleApplication`. Le API rispondono su http://localhost:8080/api/v1, Swagger UI su http://localhost:8080/swagger-ui.html.

Con il database vuoto, al primo avvio vengono creati utenti di prova, tutti con password `Calcetto2026`: `admin@`, `ospite@`, `coach@`, i giocatori `paolo@`, `luca@`, `marco@`, `davide@` e `simone@` (in attesa di approvazione), tutti `@demo.local`. Ci sono anche cinque partite intorno alla data di oggi. In produzione non vengono mai creati.

**4. Frontend.**

```bash
cd frontend
npm install
npm run dev
```

Apri http://localhost:5173: le chiamate `/api` vengono girate al backend sulla porta 8080.

**5. Test.**

```bash
cd backend
./mvnw test                   # usa il database squadra_test

cd frontend
npm run lint && npm test && npm run build
```

## Pubblicazione su Render

Tutto è descritto in [`render.yaml`](render.yaml): database PostgreSQL, backend in un container Docker e sito statico del frontend.

1. Su Render crea un **Blueprint** collegato a questo repository.
2. Inserisci le variabili che Render chiede: `MAIL_FROM_EMAIL`, `MAILJET_API_KEY`, `MAILJET_API_SECRET` (vedi [email](docs/email-setup.md)) e i dati del primo Admin, `ADMIN_BOOTSTRAP_EMAIL` e `ADMIN_BOOTSTRAP_PASSWORD`.
3. Al primo avvio il backend crea l'Admin. Poi **cancella** le variabili `ADMIN_BOOTSTRAP_*` dal pannello: non servono più e la password non deve restare lì.
4. Per l'account del valutatore imposta `REVIEWER_EMAIL` e `REVIEWER_PASSWORD` con gli stessi valori della tabella sopra. Viene creato al riavvio, solo se non esiste già. A valutazione finita sospendilo dalla pagina *Utenti*.

Ogni push su `main` va online da solo e i test non girano su Render: lanciali prima in locale. Variabili, limiti del piano gratuito e backup sono in [deployment](docs/deployment.md).

## Problemi incontrati

**Variabili di Windows che vincono sul `.env`.** Se in Windows esiste già una variabile con lo stesso nome di una del `.env` (magari creata per un altro progetto), il backend usa quella e ignora il file. I sintomi visti:

| Variabile | Cosa succede |
|---|---|
| `DB_PASSWORD` | all'avvio: `autenticazione con password fallita per l'utente "squadra"` |
| `MAILJET_API_KEY` / `MAILJET_API_SECRET` | nella pagina Email: `Mailjet ha risposto 401` |
| `PORT` | il backend parte su un'altra porta e il frontend riceve `502` |

Per controllare: tasto Windows → "variabili d'ambiente" → *Modifica le variabili d'ambiente relative al tuo account*. Puoi cancellare la variabile e riaprire IntelliJ e i terminali, oppure sovrascriverla solo per questo progetto: in IntelliJ da *Run → Edit Configurations… → Environment variables*, in PowerShell prima dell'avvio:

```powershell
$env:PORT = '8080'
.\mvnw.cmd spring-boot:run
```

**Maven non scarica le dipendenze ("PKIX path building failed").** Succede quando un antivirus o un proxy ispeziona il traffico HTTPS. Su Windows si risolve facendo usare a Java i certificati di sistema, solo nella sessione corrente:

```powershell
$env:MAVEN_OPTS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT'
```

Non va messo nei file del progetto: su Render (Linux) romperebbe la build.

**Limite ai tentativi di login aggirabile su Render.** Dietro i proxy di Render e Cloudflare, un attaccante poteva inventarsi l'IP a ogni richiesta con l'header `X-Forwarded-For` e il limite di 10 login al minuto non scattava mai. Ora l'IP vero si ricava leggendo l'header da destra (dettagli in [sicurezza](docs/security.md)).

**"Port scan timeout" al deploy.** Sul piano gratuito Spring Boot a volte parte troppo lentamente per Render; di solito basta un *Manual Deploy*.

**Link di conferma email usato due volte.** In sviluppo React esegue gli effetti due volte, e il secondo tentativo trovava il link già consumato. La richiesta per lo stesso link ora viene fatta una volta sola e riusata.

## Scelte tecniche

**Backend**
- **Java 25 e Spring Boot 4.1**: l'ultima versione a supporto lungo di Java e la versione stabile di Spring Boot, con tutto quello che serve già integrato.
- **Spring Security con JWT**: permessi controllati sul server per ogni richiesta. Il token di accesso dura poco e sta solo in memoria; quello di rinnovo sta in un cookie che JavaScript non può leggere.
- **PostgreSQL**: vincoli e trigger proteggono i dati anche da errori nel codice (per esempio l'audit log non si può modificare), e `SKIP LOCKED` permette una coda email affidabile.
- **Flyway**: lo schema del database cresce con migrazioni numerate, uguali in locale e in produzione.
- **Bean Validation**: le regole sui dati (email, password, importi) si scrivono una volta sola, sulle classi.
- **Thymeleaf per le email**: template HTML veri, con un layout comune e pezzi riutilizzabili, versione solo testo e protezione automatica dall'HTML inserito dagli utenti. Si usa solo il motore, senza pagine web.
- **Mailjet**: piano gratuito sufficiente per una squadra, API semplice e un webhook che dice se ogni email è stata consegnata.
- **springdoc (Swagger UI)**: per provare le API dal browser durante lo sviluppo; in produzione è spento.
- **Actuator**: fornisce il controllo di salute che Render usa per capire se il deploy è riuscito.
- **OpenAI**: predisposto ma spento, per eventuali funzioni future.

**Frontend**
- **React 19 e Vite**: sviluppo veloce, e la build è un sito statico che si pubblica gratis.
- **React Router**: navigazione tra le pagine senza ricaricare.
- **Bootstrap 5.3**: griglia e moduli pronti. Le sue variabili CSS permettono di cambiare tutti i colori e di avere tema chiaro e scuro senza riscrivere i componenti.
- **Motion**: poche animazioni brevi, caricate dopo la pagina per non rallentarla, e spente se il telefono chiede meno movimento.
- **Lucide**: icone SVG coerenti al posto delle emoji.
- **Archivo (con @fontsource)**: un carattere variabile, con titoli condensati da "maglia da calcio". È servito dal sito stesso, come richiede la Content Security Policy.
- **Vitest, Testing Library e oxlint**: test delle pagine come le usa un utente, e un controllo del codice molto veloce.

**Design**
- **Impeccable**: skill di design per Claude Code, usata per definire la direzione grafica ("Riflettori sul sintetico", i colori del campetto la sera), rivedere ogni pagina con screenshot reali e scrivere [DESIGN.md](DESIGN.md), il riferimento per le modifiche future.

**Pubblicazione**
- **Render**: piano gratuito per database, backend e sito; tutta l'infrastruttura sta in un file (`render.yaml`) e si ricrea da zero.
- **Docker**: Render non ha un ambiente Java pronto. L'immagine compila con il JDK e gira con il solo JRE, con un utente senza privilegi.
