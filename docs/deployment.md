# Deployment su Render

L'app è online su https://squadra-app.onrender.com, con il backend su `https://squadra-api.onrender.com`. Tutto è descritto nel blueprint [`render.yaml`](../render.yaml); il backend si costruisce con [`backend/Dockerfile`](../backend/Dockerfile).

## I tre servizi

| Servizio | Tipo | Dettagli |
|---|---|---|
| `squadra-db` | PostgreSQL 18 (gratuito, Francoforte) | raggiungibile solo dalla rete interna di Render |
| `squadra-api` | Web Service Docker | controllo di salute su `/actuator/health`; nuovo deploy a ogni push su `main` |
| `squadra-app` | sito statico | build `npm ci && npm run build` nella cartella `frontend`, pubblica `dist` |

Render non ha un ambiente Java pronto, quindi il backend gira in un container: si compila con il JDK 25 e si esegue con il solo JRE 25, con un utente non root e la JVM limitata al 75% della memoria.

Durante la build **i test non girano** (servirebbe un database). Ogni push su `main` va online, quindi prima di fare push lancia in locale `./mvnw test` e `npm test`.

I piani gratuiti vanno indicati esplicitamente (`plan: free`), altrimenti Render sceglie un piano a pagamento. Prezzi e limiti cambiano: controllali su https://render.com/pricing.

## Variabili del backend

Le imposta il blueprint, tranne dove è indicato diversamente.

| Variabile | Valore |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | presi dal database `squadra-db`; in alternativa un `DB_URL` completo |
| `JWT_SECRET` | generato da Render |
| `MAILJET_WEBHOOK_SECRET` | generato da Render; serve per il webhook di Mailjet (vedi `email-setup.md`) |
| `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` | `https://squadra-app.onrender.com` |
| `MAIL_MODE` | `MAILJET` |
| `MAIL_FROM_NAME` | `Gestionale Squadra` |
| `MAIL_FROM_EMAIL`, `MAILJET_API_KEY`, `MAILJET_API_SECRET` | da inserire a mano alla creazione; mai nel repository |
| `ADMIN_BOOTSTRAP_*` | da inserire alla creazione per il primo Admin, poi da **cancellare** (vedi README) |
| `REVIEWER_EMAIL`, `REVIEWER_PASSWORD` | facoltative: creano l'account Admin per chi valuta il progetto (vedi README) |
| `OPENAI_ENABLED` | `false` |
| `PORT` | la imposta Render |

## Regole del sito statico

- `/api/*` viene girato al backend. Per il browser le API stanno sullo stesso sito, così il cookie di sessione funziona senza richieste tra domini diversi.
- Tutto il resto finisce su `/index.html`, che lascia la navigazione a React Router. I file che esistono davvero (`favicon.svg`, `sw.js`, `assets/*`) vengono serviti così come sono.
- Gli header di sicurezza valgono per tutte le pagine e sono gli stessi di [`frontend/security-headers.js`](../frontend/security-headers.js); un test controlla che `render.yaml` non se ne discosti. In locale si provano con `npm run build && npm run preview`.
- `/`, `/index.html` e `/sw.js` non si tengono in cache, così il browser prende subito la versione nuova. I file in `/assets/*` hanno un hash nel nome e restano in cache per un anno.

Se un nome di servizio fosse già occupato, Render aggiunge un suffisso e l'indirizzo cambia. In quel caso vanno aggiornati in `render.yaml` i tre URL segnati con il commento "URL".

## Il vecchio service worker

L'app per ora non è installabile. I telefoni che l'hanno aperta quando lo era hanno ancora il vecchio service worker, che ogni tanto ricontrolla `/sw.js`. Ora quel file svuota le cache, si disinstalla e ricarica la pagina. Va lasciato online finché quei dispositivi non sono passati tutti dal sito.

## Cose da sapere sul piano gratuito

- **Il backend si addormenta** dopo un po' di inattività. La prima richiesta dopo la pausa è lenta, mentre il container riparte. Mentre dorme non partono le email in coda né i promemoria delle 9:00: le email partono al risveglio e i promemoria saltati vengono recuperati dal giro successivo, senza doppioni.
- **Database gratuito**: verifica se ha una scadenza e se include i backup. Per un uso vero conviene un piano a pagamento.
- **"Port scan timeout"** durante il deploy: il backend ci ha messo troppo ad aprire la porta. Sul piano gratuito capita ogni tanto; di solito basta un *Manual Deploy*. Se succede spesso, cerca nei log `ERROR` o `Caused by` e nella scheda *Events* eventuali problemi di memoria.

## Verifiche fatte sul servizio online

| Verifica | Esito |
|---|---|
| Controllo di salute del backend | `UP` |
| Header di sicurezza del sito e HSTS | presenti |
| API sulla stessa origine e navigazione nelle pagine interne | funzionano |
| Regole di cache | corrette |
| App in un browser vero con la CSP attiva | nessuna violazione |
| Primo Admin creato dalle variabili `ADMIN_BOOTSTRAP_*` | funziona |
| Email reali con Mailjet | consegnate |
| Limite ai tentativi di login con `X-Forwarded-For` falsificato | scatta all'11° tentativo (vedi `security.md`) |

## Backup

Il database accetta connessioni solo dalla rete interna di Render. Per fare un backup dal tuo PC aggiungi per poco il tuo IP in *Access Control* del database e toglilo subito dopo.

- Backup: `pg_dump --format=custom --no-owner -d "<external-connection-string>" -f backup.dump`
- Ripristino: `pg_restore --no-owner --clean --if-exists -d "<connection-string>" backup.dump`

I dump contengono dati personali: tienili fuori dal repository (`.gitignore` li esclude già), cifrati, e prova ogni tanto a ripristinarli su un database di prova.
