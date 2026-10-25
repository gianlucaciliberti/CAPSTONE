# Sicurezza

Backend e frontend sono stati rivisti prima della pubblicazione, e le difese principali sono state riprovate anche sul servizio online.

## Le basi

- **Tutto chiuso, salvo eccezioni.** Senza autenticazione si raggiungono solo il controllo di salute, `/api/v1/public/**`, gli endpoint di accesso elencati sotto e Swagger, se è acceso con `SWAGGER_ENABLED=true`. Tutto il resto risponde 401.
- **Nessuna sessione sul server**: niente form di login né HTTP Basic, solo token.
- **CORS stretto**: sono ammesse solo le origini in `CORS_ALLOWED_ORIGINS`, con metodi e header elencati uno per uno.
- **Password**: BCrypt con costo 12; almeno 10 caratteri, con almeno una lettera e un numero, e al massimo 72 byte. Le regole valgono sia nel backend sia nel frontend.
- **Errori**: messaggi generici, mai lo stack trace; i dettagli finiscono solo nei log.
- **Audit log**: un trigger di PostgreSQL impedisce di modificarlo o cancellarlo. Registra registrazioni, conferme email, approvazioni, rifiuti, sospensioni, cambi di ruolo e di password, e ogni modifica a partite, convocazioni, risultati, pagamenti ed email.
- **Segreti**: solo nelle variabili d'ambiente; il file `.env` non entra in Git. In produzione database, JWT, CORS e modalità email non hanno valori predefiniti: se manca qualcosa, il backend non parte.
- **Header delle API**: `Referrer-Policy: no-referrer`, così i token nei link delle email non escono verso altri siti; una CSP che non permette di caricare niente né di incorporare le risposte in un frame; `Permissions-Policy` che spegne fotocamera, microfono, posizione, pagamenti e USB; più gli header standard di Spring Security (`nosniff`, `X-Frame-Options: DENY`, niente cache, HSTS su HTTPS).

## Accesso

| Endpoint (`/api/v1/auth`) | Chi può chiamarlo | Limite per IP |
|---|---|---|
| `POST /register` | tutti | 5 all'ora |
| `POST /verify-email` | tutti | 20 ogni 15 min |
| `POST /resend-verification` | tutti | 5 all'ora |
| `POST /login` | tutti | 10 al minuto |
| `POST /refresh` | cookie + header anti-CSRF | 30 al minuto |
| `POST /logout` | cookie + header anti-CSRF | — |
| `POST /forgot-password` | tutti | 5 all'ora |
| `POST /reset-password` | tutti | 10 ogni 15 min |
| `GET /me` | utenti autenticati | — |
| `POST /change-password` | utenti autenticati | 10 ogni 15 min |

**Token.** L'access token è un JWT firmato con `JWT_SECRET`, dura 15 minuti e contiene solo l'id dell'utente. Il frontend lo tiene in memoria, mai in localStorage. Il refresh token è un valore casuale in un cookie `HttpOnly`, `Secure` e `SameSite=Strict`, valido 14 giorni; nel database se ne salva solo l'hash.

**Rinnovo e revoca.** A ogni rinnovo il refresh token cambia. Se qualcuno riusa un token già sostituito (segno che potrebbe essere stato rubato), si chiude l'intera sessione. Il frontend evita i falsi allarmi mettendo in fila i rinnovi, anche tra schede diverse. Il logout chiude la sessione corrente; cambio o reset della password, sospensione e rifiuto le chiudono tutte.

**Ruolo e stato si rileggono dal database a ogni richiesta**, così una sospensione o un cambio di ruolo valgono subito, anche con un token già emesso.

**CSRF.** Rinnovo e logout si basano sul cookie `SameSite=Strict` e chiedono anche l'header `X-Requested-With`, che un altro sito non può aggiungere senza passare dal controllo CORS. Le altre API usano il token nell'header `Authorization`, che non è esposto al CSRF.

**Troppi tentativi.** Dopo 5 password sbagliate l'account si blocca per 15 minuti. In più c'è un limite per IP (tabella sopra) con risposta 429. I contatori stanno in memoria: con più istanze del backend servirebbe un archivio condiviso, per esempio Redis.

**Nessun indizio sugli utenti esistenti.** Registrazione, login, recupero password e nuovo invio della verifica rispondono sempre allo stesso modo, che l'email esista o no. Lo stato dell'account (in attesa, sospeso, rifiutato) si scopre solo dopo aver inserito la password giusta.

**Registrazione.** Il server assegna sempre ruolo Giocatore e stato "in attesa", e manda subito il link di conferma (valido 24 ore). L'Admin può approvare solo chi ha confermato l'email. Solo gli account attivi ottengono i token.

**Link via email** (conferma e reset): monouso e casuali, salvati solo come hash. Un link nuovo annulla i precedenti, e se ne può chiedere al massimo uno ogni 2 minuti. Il testo di queste email si cancella dalla coda dopo l'invio. Ogni notte si eliminano i token scaduti da più di 7 giorni.

**Account creati all'avvio.** Il primo Admin nasce dalle variabili `ADMIN_BOOTSTRAP_*`, usate una volta sola: nel codice non ci sono password. Allo stesso modo, le variabili `REVIEWER_*` creano l'account per chi valuta il progetto (azione `REVIEWER_CREATED` nell'audit log). Le sue credenziali sono pubblicate apposta nel README, quindi a valutazione finita l'account va sospeso. Gli utenti di prova esistono solo in sviluppo, con il database vuoto.

## Chi può fare cosa

I ruoli si sommano: l'Admin ha anche i permessi dell'Allenatore, che ha quelli del Giocatore. Ogni endpoint controlla il ruolo nel backend; le rotte protette del frontend servono solo all'interfaccia. Le richieste non possono contenere ruolo, stato o autore: li decide sempre il server.

- **Utenti**: un Admin non può cambiare il proprio stato, e l'ultimo Admin attivo non può essere declassato né sospeso.
- **Disponibilità**: ognuno modifica solo la propria, finché la partita non è giocata e la convocazione non è pubblicata. Il riepilogo della squadra lo vedono Allenatori e Admin; correggere la risposta di un altro può solo l'Admin, e resta nell'audit log.
- **Partite**: le vedono tutti gli utenti attivi; crearle, modificarle e annullarle può solo l'Admin.
- **Convocazioni**: le gestiscono Allenatori e Admin. I giocatori non vedono le bozze né le disponibilità degli altri. Per convocare chi non è disponibile o non ha risposto serve una conferma esplicita, controllata anche dal server. Niente si cancella: restano le revisioni.
- **Risultati e tabellini**: li vedono tutti; li modifica solo l'Admin, per partite già iniziate e non annullate. Ogni modifica resta nell'audit log con i valori precedenti.
- **Pagamenti**: ognuno vede solo le proprie quote, senza la nota interna dell'Admin. Tutto il resto (creare, confermare, esonerare, riaprire, mandare promemoria) è dell'Admin. Niente si cancella.
- **Email**: Allenatori e Admin vedono lo stato degli invii ma non il contenuto; gli errori del provider, i nuovi tentativi e gli annullamenti sono solo dell'Admin. Il webhook di Mailjet è protetto da una password (`MAILJET_WEBHOOK_SECRET`) ed è spento se manca.
- **Dashboard**: mostra sempre i dati di chi è collegato. Allo staff aggiunge solo conteggi e partite, mai dati personali di altri utenti.

Tutte le modifiche importanti usano il controllo di versione (chi salva per secondo riceve 409) e finiscono nell'audit log.

## Il sito

Gli header del sito sono in un solo file, [`frontend/security-headers.js`](../frontend/security-headers.js). Li usa `npm run preview` in locale, e `render.yaml` li riporta identici (lo controlla un test).

- **Content Security Policy** senza `unsafe-inline` né `unsafe-eval`: script, stili, font e chiamate solo dal sito stesso. Le immagini ammettono anche `data:`, per le piccole icone SVG dentro il CSS di Bootstrap. Il sito non si può incorporare in un frame.
- `index.html` non ha script, stili o gestori di eventi scritti nella pagina: lo controlla `securityHeaders.test.js`. L'app è stata provata in un browser vero con la CSP attiva, senza errori.
- Il frontend non usa `innerHTML` né `eval`: React mette in sicurezza tutto il testo. Dopo il login torna solo a pagine interne dell'app, mai a indirizzi esterni.

## L'IP vero dietro i proxy

Su Render le richieste passano da Cloudflare e dal proxy di Render prima di arrivare al backend. L'IP del client e lo schema `https` li ricava [`ClientIpFilter`](../backend/src/main/java/it/squadra/gestionale/security/ClientIpFilter.java), il primo filtro della catena.

Questo filtro è nato da un problema trovato online. Cloudflare e Render **aggiungono** il proprio indirizzo in fondo a `X-Forwarded-For` senza togliere quelli scritti dal client. Il filtro standard di Spring leggeva il primo valore, che il client può inventare: cambiandolo a ogni richiesta, il limite di 10 login al minuto non scattava mai.

Gli header misurati sul servizio vero erano così:

| Percorso | `X-Forwarded-For` ricevuto dal backend |
|---|---|
| Diretto al backend | `[valori del client], <IP client>, 172.x (Cloudflare), 10.x (Render)` |
| Dal sito (`/api/*`) | `[valori del client], <IP client>, 162.158.x, 162.158.x (Cloudflare), 74.220.48.x (sito Render), 172.x (Cloudflare), 10.x` |

Ora l'elenco si legge da destra, saltando gli indirizzi dell'infrastruttura (reti private, Cloudflare, Render): il primo che resta è il client vero, perché quelli falsi stanno sempre più a sinistra. Gli header si considerano solo se la connessione arriva da un indirizzo fidato. `CF-Connecting-IP` non serve: passando dal sito contiene l'indirizzo del sito stesso. Se Cloudflare o Render cambiano i loro indirizzi, l'elenco in `ClientIpFilter` va aggiornato.

## Dipendenze e segreti

- Nel repository e nella sua storia non ci sono segreti. L'unico valore che ci somiglia è la chiave finta dei test in `application-test.yml`.
- `npm audit` non segnalava vulnerabilità al momento della revisione: va ripetuto prima di ogni rilascio.
- Le librerie del backend le gestisce Spring Boot. Per avere le correzioni di sicurezza basta alzare la versione nel `pom.xml` alle nuove patch 4.1.x.
