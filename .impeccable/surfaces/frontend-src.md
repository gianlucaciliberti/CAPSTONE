---
version: 1
slug: "frontend-src"
primary_target: "frontend/src"
related_targets: []
---

## Scope

Tutta l'app frontend (React SPA in `frontend/src`): shell, navigazione, home dashboard, partite, tabellino, risultati e statistiche, disponibilità, convocazioni, pagamenti, pagine staff e di accesso. Modalità: Operate.

## Audience and task

Giocatori dal telefono (rispondere, sapere se convocati, quote), allenatore e admin anche da PC (convocazioni, tabellini, pagamenti, utenti). Momento da rendere migliore: risultati e statistiche, l'orgoglio della squadra. Da evitare: effetto videogioco, freddezza aziendale, pesantezza su rete lenta.

## Constraints

Bootstrap 5.3 come base; Motion e React Bits (versioni CSS) ammessi; CSP `style-src 'self'` senza stili iniettati a runtime; tema chiaro e scuro con interruttore; `prefers-reduced-motion`; logica e divisione delle pagine invariate; mobile-first con barra inferiore.

## Direction contract

THESIS: Valhalla FC usa un'app con i colori e il ritmo del campetto su cui gioca alle 21: erba a strisce di taglio, linee bianche, casacche lime. Rifiuta lo schema di categoria delle card bianche identiche con accento blu.

OWN-WORLD: toni dell'erba sintetica (verde profondo e verde taglio alternati come gradini di tono), bianco vernice delle linee come inchiostro e come segno di struttura, lime delle casacche riservato alle azioni e alla selezione. Un solo carattere su cinque gradini di dimensione distanti, gerarchia con peso e maiuscolo; numeri tabulari per punteggi e statistiche. Colore solo dove significa qualcosa (stato, azione, squadra). Nessun bagliore, nessun vetro.

STORY: il giocatore apre l'app e vede subito la prossima partita, risponde in un tocco, poi trova il risultato dell'ultima partita e la classifica marcatori come una cosa di cui andare fieri. Lo staff trova gli stessi strumenti di oggi, più chiari.

FIRST VIEWPORT: barra superiore verde campo con il nome Valhalla FC; prossima partita con numero progressivo, data, campo e i tre pulsanti di risposta; sotto, l'ultimo risultato a grande scala con i marcatori; barra inferiore con le cinque sezioni. Azione primaria: rispondere alla disponibilità.

FORM: candidato 5 della lista ordinata (campetto in sintetico sotto i riflettori), rinforzato da Emigre specimen, lexicon, catalogo Factory, orario su vetrini e registro di esposizione. seed:2f9daab37ab3d4a8. Mossa distintiva: il tabellino disegnato come il campo, eventi posati lungo il tempo di gioco con la metà campo come intervallo e le due metà per noi e avversari.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
