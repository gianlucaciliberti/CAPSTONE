# Architettura

## Panoramica

```
Browser ──► Sito statico React (Render Static Site)
               │  /api/*  (rewrite same-origin verso il backend)
               ▼
          Backend Spring Boot (Render Web Service) ──► PostgreSQL (Render Postgres)
               │
               ├──► Mailjet API (invio email, dal worker della coda)
               └──► OpenAI API (predisposto, disattivato)
```

- **Frontend**: SPA React servita come sito statico. Non contiene segreti: le variabili `VITE_*` finiscono nel bundle pubblico.
- **Backend**: API REST stateless sotto `/api/v1`. Tutte le autorizzazioni sono verificate qui con Spring Security; il ruolo dichiarato dal client non viene mai considerato.
- **Database**: PostgreSQL, schema gestito esclusivamente da migrazioni Flyway. Hibernate è in modalità `validate`: all'avvio verifica che le entità corrispondano allo schema ma non lo modifica mai.

### Same-origin per le API

In sviluppo il dev server di Vite inoltra `/api` al backend (`vite.config.js`). In produzione è previsto lo stesso schema con una regola di *rewrite* del sito statico Render (`/api/*` → `https://<backend>.onrender.com/api/*`): la documentazione Render conferma che la destinazione di una rewrite può essere "a full, publicly accessible URL". Così il cookie del refresh token resta first-party (`SameSite=Strict`) e non dipende dai cookie di terze parti, bloccati da molti browser.

Il comportamento di header e cookie attraverso la rewrite non è documentato in dettaglio da Render: va verificato nella fase di deployment. In alternativa: cookie `SameSite=None; Secure` con CORS restrittivo, oppure un dominio personalizzato condiviso.

## Versioni scelte

| Componente | Versione | Motivo |
|---|---|---|
| Java | 25 (LTS, Temurin) | LTS più recente, supportata da Spring Boot 4.1 |
| Spring Boot | 4.1.1 | Versione stabile predefinita su start.spring.io (ottobre 2026) |
| Maven | wrapper 3.3.4 (Maven 3.10.0) | Incluso nel repository (`mvnw`), nessuna installazione richiesta |
| springdoc-openapi | 3.1.1 | Linea 3.x compatibile con Spring Boot 4 |
| Thymeleaf | 3.1.5 (gestita da Spring Boot) | Template delle email; solo il motore, senza integrazione web |
| PostgreSQL | 18 in locale | Usare la stessa versione major anche su Render |
| Node.js | ≥ 22.22 (testato con 24.16) | Richiesto da React Router 8 |
| React | 19.2 | |
| Vite | 8.3 | |
| React Router | 8.4 | |
| Bootstrap | 5.3.8 | Solo CSS; i componenti interattivi sono gestiti in React |
| Vitest + Testing Library | 5.0 / 16.3 | Test del frontend |
## Struttura del repository

```
CAPSTONE/
├── render.yaml              blueprint Render: database, backend (Docker) e sito statico
├── backend/                 Spring Boot (Maven)
│   ├── Dockerfile           immagine di produzione (build con JDK 25, esecuzione con JRE 25, utente non root)
│   ├── db/init-local.sql    creazione database locali (dev/test)
│   └── src/main/java/it/squadra/gestionale/
│       ├── config/          AppProperties (tutta la configurazione app.*), AppConfig (bean generali)
│       ├── controllers/     controller REST, uno per area di API
│       ├── dto/             un file per area (AuthDto, UserDto, CommonDto) con i record annidati
│       ├── entities/        entità JPA, con i propri enum annidati (es. User.Role, Match.MatchStatus)
│       ├── exceptions/      ApiException (con ErrorCode) e gestione centralizzata degli errori
│       ├── repositories/    repository Spring Data JPA, uno per entità
│       ├── security/        SecurityConfig (filtri, JWT, CORS, 401/403), JwtService, AuthTokens, rate limiting
│       ├── services/        logica di business (transazioni, regole, audit)
│       └── validation/      vincoli Bean Validation personalizzati (annotazione e validatore nello stesso file)
├── frontend/                React + Vite
│   └── src/
│       ├── api/             client HTTP verso il backend
│       ├── components/      componenti riutilizzabili e layout
│       ├── pages/           pagine collegate alle rotte
│       └── styles/          tema
└── docs/                    documentazione tecnica
```

Il backend è organizzato **per livello tecnico**: ogni package raccoglie le classi con lo stesso compito (tutte le entità in `entities`, tutti i repository in `repositories`, ecc.). Per contenere il numero di file, i tipi piccoli e strettamente legati sono **annidati** nella classe a cui appartengono (enum nelle entità, DTO raggruppati per area, `ErrorCode` in `ApiException`); entità e repository restano uno per file, come richiesto da JPA e Spring Data. I test seguono la stessa struttura in `src/test/java`, raggruppati con classi `@Nested`.

## Scelte principali

- **Fuso orario**: gli istanti sono `TIMESTAMPTZ` salvati in UTC (`hibernate.jdbc.time_zone=UTC`) e mappati su `Instant`. La conversione in `Europe/Rome` avviene ai bordi (API e interfaccia). Un bean `Clock` con zona `Europe/Rome` rende testabile la logica sulle date.
- **Errori**: formato unico RFC 9457 (`application/problem+json`) con campo `code` stabile per il frontend.
- **Concorrenza**: `@Version` (optimistic locking) sulle entità modificabili; un conflitto restituisce 409.
- **Storico**: nessuna cancellazione fisica di convocazioni, pagamenti e audit log (vedi `database.md`).
- **Email affidabili**: transactional outbox su tabella `email_notifications` (vedi `email-setup.md`).
- **Integrazioni esterne opzionali**: Mailjet e OpenAI sono disattivabili da configurazione; in locale si sviluppa senza credenziali (`MAIL_MODE=LOG`, `OPENAI_ENABLED=false`).

## Interfaccia mobile-first

L'app è usata soprattutto dallo smartphone (a bordo campo, in spogliatoio). Le regole di `frontend/src/styles/theme.css` partono dallo schermo piccolo e si arricchiscono con media query `min-width` (768px e 992px, come i breakpoint `md` e `lg` di Bootstrap):

- **Barra di navigazione inferiore** (`BottomNav`) con le sezioni principali (Home, Calendario, Disponibilità, Risultati, Pagamenti), raggiungibili con il pollice; da 992px sparisce e le stesse voci tornano nel menu in alto. Rispetta le aree sicure degli schermi con notch (`viewport-fit=cover`, `env(safe-area-inset-bottom)`).
- **Tabelle impilate** (`table-stack`): su mobile ogni riga diventa una scheda con le etichette dei campi (`data-label`), da tablet torna una tabella. Usate per utenti, email e disponibilità della squadra.
- **Filtri a chip** (`filter-chips`) scorrevoli in orizzontale su mobile.
- **Tocco**: aree di almeno 44px sui dispositivi touch e campi a 16px su mobile (evita lo zoom automatico di iOS al focus).
- Griglie Bootstrap con colonne che si affiancano solo da `md`/`lg` in su; pulsanti e azioni che vanno a capo invece di uscire dallo schermo.

## PWA

L'app è installabile sulla schermata Home (manifest `frontend/public/manifest.webmanifest`, icone in `public/icons`, anche *maskable* per Android e *apple-touch-icon* per iOS) e si apre a schermo intero.

- **Service worker** (`public/sw.js`, registrato da `src/pwa.js` solo in produzione): mette in cache l'app shell (HTML, JavaScript e CSS della build, icone, `offline.html`). Navigazione *network-first* con ripiego sulla shell in cache; file con hash *cache-first*; **le API non passano mai dalla cache**, per non conservare sul dispositivo dati personali o risposte di autenticazione e per mostrare sempre dati aggiornati.
- **Aggiornamenti**: a ogni deploy cambia la versione del service worker; la nuova versione resta in attesa finché l'utente non tocca "Aggiorna" nell'avviso in alto, poi la pagina si ricarica una volta.
- **Offline**: avviso "Sei offline" in tutte le pagine; senza rete al primo accesso viene mostrata `offline.html`.
- **Installazione**: nel Profilo, pulsante "Installa l'app" dove il browser lo consente (Chrome, Edge, Android) e istruzioni per Safari su iPhone/iPad.
- Notifiche push non incluse: gli avvisi restano via email.

## Fasi di sviluppo

1. Struttura, configurazione, database e migrazioni ✅
2. Autenticazione e ruoli ✅
3. Calendario e disponibilità ✅
4. Convocazioni e email ✅
5. Risultati e tabellini ✅
6. Pagamenti ✅
7. Dashboard responsive e predisposizione PWA ✅
8. Test, sicurezza e deployment ✅ (online su Render)
