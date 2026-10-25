# Architettura

## Come è fatta l'app

```
Browser ──► Sito statico React (Render Static Site)
               │  /api/*  (rewrite sulla stessa origine)
               ▼
          Backend Spring Boot (Render Web Service) ──► PostgreSQL (Render Postgres)
               │
               ├──► Mailjet (invio email, dalla coda)
               └──► OpenAI (predisposto, spento)
```

- **Frontend**: una single page app React servita come sito statico. Non contiene segreti: tutto ciò che sta nelle variabili `VITE_*` finisce nel codice pubblico.
- **Backend**: API REST senza sessione sotto `/api/v1`. I permessi si controllano solo qui, con Spring Security; il ruolo che dichiara il client non conta.
- **Database**: PostgreSQL. Lo schema lo creano solo le migrazioni Flyway; Hibernate si limita a controllare all'avvio che le entità corrispondano (`validate`).

Le API stanno sulla stessa origine del sito. In sviluppo ci pensa il proxy di Vite, in produzione una rewrite del sito statico Render (`/api/*` → backend). Così il cookie del refresh token è di prima parte (`SameSite=Strict`) e non risente del blocco dei cookie di terze parti.

## Versioni

| Componente | Versione | Note |
|---|---|---|
| Java | 25 (LTS, Temurin) | |
| Spring Boot | 4.1.1 | |
| Maven | wrapper 3.3.4 (Maven 3.10.0) | incluso (`mvnw`), niente da installare |
| springdoc-openapi | 3.1.1 | Swagger UI |
| Thymeleaf | 3.1.5 | solo per i template delle email |
| PostgreSQL | 18 | stessa versione in locale e su Render |
| Node.js | ≥ 22.22 (provato con 24.16) | richiesto da React Router 8 |
| React / Vite / React Router | 19.2 / 8.3 / 8.4 | |
| Bootstrap | 5.3.8 | solo il CSS; le parti interattive sono in React |
| Vitest + Testing Library | 5.0 / 16.3 | test del frontend |

## Struttura del repository

```
CAPSTONE/
├── render.yaml              blueprint Render: database, backend (Docker) e sito statico
├── backend/                 Spring Boot (Maven)
│   ├── Dockerfile           immagine di produzione (JDK 25 per la build, JRE 25 per l'esecuzione, utente non root)
│   ├── db/init-local.sql    crea i database locali (sviluppo e test)
│   └── src/main/java/it/squadra/gestionale/
│       ├── config/          AppProperties (configurazione app.*), AppConfig
│       ├── controllers/     un controller REST per area
│       ├── dto/             un file per area, con i record annidati
│       ├── entities/        entità JPA con i loro enum annidati
│       ├── exceptions/      ApiException e gestione centralizzata degli errori
│       ├── repositories/    un repository per entità
│       ├── security/        Spring Security, JWT, CORS, rate limiting
│       ├── services/        logica di business
│       └── validation/      vincoli di validazione personalizzati
├── frontend/                React + Vite
│   └── src/                 api/, components/, pages/, styles/
└── docs/                    questa documentazione
```

Il backend è diviso per livello tecnico: tutte le entità insieme, tutti i repository insieme, e così via. Per non moltiplicare i file, i tipi piccoli vivono dentro la classe a cui appartengono (enum nelle entità, DTO raggruppati per area). I test seguono la stessa struttura e sono raggruppati con classi `@Nested`.

## Scelte principali

- **Orari**: si salvano in UTC (`TIMESTAMPTZ`, `Instant`) e si convertono in `Europe/Rome` solo quando escono verso l'utente. Un `Clock` iniettato rende testabile la logica sulle date.
- **Errori**: sempre nel formato RFC 9457 (`application/problem+json`), con un campo `code` stabile che il frontend può leggere.
- **Modifiche concorrenti**: le entità modificabili hanno una versione; se due persone salvano insieme, la seconda riceve 409.
- **Storico**: convocazioni, pagamenti e audit log non si cancellano mai (vedi `database.md`).
- **Email**: passano da una coda nel database, così non si perdono (vedi `email-setup.md`).
- **Servizi esterni spegnibili**: in locale si lavora senza credenziali (`MAIL_MODE=LOG`, `OPENAI_ENABLED=false`).

## Interfaccia

L'app si usa soprattutto dal telefono, quindi gli stili in `frontend/src/styles/theme.css` partono dallo schermo piccolo e si allargano a 768px e 992px (i breakpoint `md` e `lg` di Bootstrap).

- Sul telefono una **barra in basso** porta alle cinque sezioni principali; da 992px le stesse voci tornano nel menu in alto. La barra rispetta il notch (`env(safe-area-inset-bottom)`).
- Le tabelle dello staff (utenti, email, disponibilità) su mobile diventano **schede**, ognuna con le etichette dei campi.
- I **filtri** sono chip che scorrono in orizzontale.
- Le aree da toccare sono di almeno 44px e i campi a 16px, così iOS non ingrandisce la pagina.
- Senza connessione compare l'avviso "Sei offline".

L'app per ora non è installabile: la PWA è stata tolta e tornerà in futuro. È rimasto solo `public/sw.js`, che spegne il vecchio service worker sui telefoni che l'avevano (vedi `deployment.md`). Notifiche push non previste: gli avvisi arrivano per email.

### Aspetto grafico

La direzione si chiama "Riflettori sul sintetico": i colori del campetto su cui la squadra gioca la sera. Il sistema completo (colori, caratteri, componenti, regole) è in [`DESIGN.md`](../DESIGN.md); il contesto di prodotto in [`PRODUCT.md`](../PRODUCT.md).

In breve:

- tema chiaro e scuro, con un interruttore in alto che ricorda la scelta sul dispositivo;
- verdi dell'erba come sfondi, bianco delle linee per il testo sul campo, lime delle casacche solo per azioni e selezioni;
- un solo carattere (Archivo, servito dal sito), titoli condensati e numeri incolonnati;
- icone Lucide, nessuna emoji; pallone, cartellini e linee del campo sono disegnati in CSS;
- animazioni brevi con Motion, caricato a parte per non rallentare l'avvio, e spente se il dispositivo chiede meno movimento.

Le pagine più importanti sono pezzi di campo: la prossima partita in home (con la risposta alla disponibilità in un tocco), il bilancio della squadra, l'accesso, la pagina non trovata. Il tabellino è disegnato come il campo visto dall'alto: il tempo scorre verso il basso, la metà campo è l'intervallo (due tempi da 25', costante `HALF_LENGTH` in `ScoresheetPages.jsx`), a sinistra i nostri eventi e a destra quelli avversari. Quando una lista è vuota compare il cerchio di centrocampo con una frase che spiega cosa manca.

## Fasi di sviluppo

1. Struttura, configurazione, database e migrazioni ✅
2. Autenticazione e ruoli ✅
3. Calendario e disponibilità ✅
4. Convocazioni e email ✅
5. Risultati e tabellini ✅
6. Pagamenti ✅
7. Dashboard responsive ✅ (la PWA è stata tolta in seguito e tornerà più avanti)
8. Test, sicurezza e deployment ✅ (online su Render)
9. Restyling del frontend ✅
