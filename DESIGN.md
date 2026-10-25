---
name: Valhalla FC
description: Gestionale della squadra di calcetto, nei colori del campetto in sintetico sotto i riflettori.
colors:
  turf-950: "#08201a"
  turf-900: "#0b2c22"
  turf-800: "#0e3b2c"
  turf-700: "#17583f"
  turf-600: "#1f7a55"
  line-white: "#f2f5ef"
  bib-lime: "#c6f432"
  bib-lime-strong: "#d8ff5e"
  day-ground: "#eaf1ec"
  day-surface: "#ffffff"
  day-surface-raised: "#f4f8f5"
  day-ink: "#0f1f19"
  day-muted: "#4a5f54"
  day-rule: "#cddbd2"
  night-ground: "#0a1813"
  night-surface: "#10241b"
  night-surface-raised: "#153023"
  night-ink: "#e7efe9"
  night-muted: "#9fb5a8"
  night-rule: "#24453a"
  card-yellow: "#ffd43b"
  card-red: "#ff5a4f"
typography:
  display:
    fontFamily: "Archivo Variable, system-ui, sans-serif"
    fontSize: "clamp(2.25rem, 7vw, 3.25rem)"
    fontWeight: 820
    lineHeight: 1.1
    letterSpacing: "-0.01em"
    fontVariation: "'wdth' 78"
  headline:
    fontFamily: "Archivo Variable, system-ui, sans-serif"
    fontSize: "1.75rem"
    fontWeight: 760
    lineHeight: 1.1
    letterSpacing: "-0.01em"
    fontVariation: "'wdth' 78"
  title:
    fontFamily: "Archivo Variable, system-ui, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 760
    lineHeight: 1.1
  body:
    fontFamily: "Archivo Variable, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.5
    fontFeature: "'tnum'"
  label:
    fontFamily: "Archivo Variable, system-ui, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 700
    letterSpacing: "0.04em"
    fontVariation: "'wdth' 85"
rounded:
  sm: "3px"
  md: "4px"
  lg: "6px"
  pill: "50rem"
spacing:
  xs: "0.25rem"
  sm: "0.5rem"
  md: "1rem"
  panel: "1.25rem"
  panel-wide: "1.75rem"
components:
  button-primary-day:
    backgroundColor: "{colors.turf-800}"
    textColor: "{colors.bib-lime}"
    rounded: "{rounded.md}"
  button-primary-day-hover:
    backgroundColor: "{colors.turf-700}"
    textColor: "{colors.bib-lime-strong}"
  button-primary-night:
    backgroundColor: "{colors.bib-lime}"
    textColor: "{colors.turf-900}"
    rounded: "{rounded.md}"
  button-primary-night-hover:
    backgroundColor: "{colors.bib-lime-strong}"
    textColor: "{colors.turf-950}"
  button-on-pitch:
    backgroundColor: "{colors.bib-lime}"
    textColor: "{colors.turf-900}"
    rounded: "{rounded.md}"
  pitch-panel:
    backgroundColor: "{colors.turf-800}"
    textColor: "{colors.line-white}"
    rounded: "{rounded.lg}"
    padding: "1.25rem"
  surface-panel-day:
    backgroundColor: "{colors.day-surface}"
    textColor: "{colors.day-ink}"
    rounded: "{rounded.lg}"
    padding: "1.25rem"
  surface-panel-night:
    backgroundColor: "{colors.night-surface}"
    textColor: "{colors.night-ink}"
    rounded: "{rounded.lg}"
    padding: "1.25rem"
  filter-chip:
    textColor: "{colors.turf-700}"
    rounded: "{rounded.pill}"
  input-day:
    backgroundColor: "{colors.day-surface}"
    textColor: "{colors.day-ink}"
    rounded: "{rounded.md}"
---

# Design System: Valhalla FC

## Overview

**Creative North Star: "Riflettori sul sintetico"**

L'app ha i colori e il ritmo del campetto su cui la squadra gioca la sera: erba a strisce di taglio, linee bianche di vernice, casacche lime. È uno strumento (modalità Operate): si usa dal telefono nei giorni prima della partita, quindi chiarezza dello stato e velocità vengono prima dell'effetto. Il carattere sta nei dettagli precisi, non nelle decorazioni.

Le parti che contano davvero (la prossima partita, il bilancio, l'accesso, la pagina non trovata) sono **pezzi di campo**: verde campo in entrambi i temi, strisce di taglio, cerchio di centrocampo o lunetta disegnati in CSS, testo nel bianco delle linee. Tutto il resto vive su superfici sobrie un gradino di tono sopra il terreno. Il tabellino è disegnato come il campo visto dall'alto ed è la mossa distintiva del sistema.

Rifiutato in modo esplicito: lo schema di categoria delle card bianche identiche con accento blu, l'effetto videogioco, la freddezza aziendale, bagliori e vetro.

**Key Characteristics:**
- Due temi, giorno e sera, sulle variabili di Bootstrap 5.3; interruttore che rispetta la preferenza del dispositivo.
- Un solo carattere variabile, gerarchia fatta da peso e larghezza; numeri tabulari ovunque.
- Colore solo dove significa qualcosa: stato, azione, squadra.
- Segni del calcio disegnati in CSS (pallone, cartellini, cerchio di centrocampo), mai emoji.
- Leggero su rete lenta: nessuna immagine, font servito dal sito, animazioni brevi e caricate dopo il primo render.

## Colors

Verdi dell'erba sintetica come gradini di tono, il bianco delle linee come inchiostro sul campo, il lime delle casacche riservato alle azioni e alla selezione.

### Primary
- **Verde campo** (turf-800, turf-700): fondo dei pezzi di campo, della barra superiore e del pulsante primario di giorno; turf-700 è link, focus e accento del tema giorno.
- **Verde notte** (turf-900, turf-950): fondo dei pezzi di campo e della barra superiore di sera; testo scuro sopra il lime.

### Secondary
- **Lime casacca** (bib-lime, bib-lime-strong per hover): azione primaria su verde campo e di sera, voce di navigazione attiva, esito "vittoria" e "disponibile" di sera, selezione del testo. Mai come fondo di grandi superfici.

### Neutral
- **Bianco linea** (line-white): titoli e testo sui pezzi di campo, linee del tabellino, minuti degli eventi.
- **Terreno giorno / sera** (day-ground, night-ground): sfondo della pagina, con una punta di verde.
- **Superficie** (day-surface, night-surface) e **superficie rialzata** (day-surface-raised, night-surface-raised): riquadri e piè di riquadro, intestazioni di tabella.
- **Inchiostro** (day-ink, night-ink), **testo secondario** (day-muted, night-muted), **riga** (day-rule, night-rule): testo, note, bordi e divisori.
- **Cartellini** (card-yellow, card-red): solo per i segni di ammonizione ed espulsione.

### Named Rules
**The Bib Rule.** Il lime è la casacca: lo indossa solo ciò che si può toccare o ciò che è selezionato. Su verde campo l'azione primaria è sempre lime, anche nel tema giorno.

**The Pitch Is Always Green Rule.** Un pezzo di campo resta verde in entrambi i temi; cambia solo il gradino (turf-800 di giorno, turf-900 di sera con un filo di riga).

**The No Bootstrap Blue Rule.** Caselle, radio, avvisi informativi, link e pulsanti prendono i colori del campo, mai il blu o il ciano predefiniti.

## Typography

**Display Font:** Archivo Variable (con system-ui, -apple-system, Segoe UI, Roboto)
**Body Font:** Archivo Variable (stesso file)

**Character:** un grotesk sportivo usato su tutta la sua gamma di larghezza: condensato e pesante per titoli, avversari e punteggi, largo normale per il testo. Ricorda i numeri delle maglie e i tabelloni senza costume.

### Hierarchy
- **Display** (820, clamp(2.25rem, 7vw, 3.25rem), 1.1; punteggi e cifre a 860 e larghezza 70%): titolo di pagina, punteggi del tabellone, totale da pagare, numeri del bilancio.
- **Headline** (760-840, 1.75rem, larghezza 72-78%, spesso in maiuscolo): nome dell'avversario, titoli delle pagine di accesso e dei pezzi di campo.
- **Title** (760, 1.25rem): titoli di sezione, orario della prossima partita, importi del registro.
- **Body** (400, 1rem, 1.5): testo e campi; 16px anche su mobile per evitare lo zoom di iOS.
- **Label** (700, 0.8125rem, 0.04em, larghezza 85%, maiuscolo): nomi delle squadre sul tabellone, sigle G V N P GF GS, intestazioni del campo nel tabellino.

### Named Rules
**The Five Steps Rule.** Cinque gradini di dimensione e basta (0.8125, 1, 1.25, 1.75 rem e il display): la gerarchia la fanno peso, larghezza e maiuscolo.

**The Tabular Rule.** Tutti i numeri sono tabulari: punteggi, minuti, importi e statistiche restano incolonnati.

## Layout

Mobile-first in un contenitore Bootstrap. Su smartphone una colonna, barra di navigazione inferiore con cinque sezioni e area sicura del notch; da 992px la barra inferiore sparisce e le voci tornano in alto. Le colonne si affiancano da 768px (md) o 992px (lg). I riquadri hanno 1.25rem di respiro interno su mobile e 1.5-1.75rem da tablet. Le tabelle staff diventano schede impilate su mobile, i filtri sono chip scorrevoli in orizzontale. Aree di tocco di almeno 44px sui dispositivi touch. Il tabellino e il punteggio restano a misura di tabellone (48rem) su schermi larghi; le pagine di accesso stanno in una colonna di 28rem.

## Elevation & Depth

Sistema piatto: nessuna ombra sotto i riquadri. La profondità viene dal tono (terreno, superficie, superficie rialzata) e dal verde dei pezzi di campo. Di sera i pezzi di campo hanno un filo interno nel colore della riga per staccarsi dal terreno scuro.

### Named Rules
**The Tonal Step Rule.** Un riquadro si distingue dal terreno di un gradino di tono, non con un'ombra grigia. Se serve un livello in più, si usa la superficie rialzata.

## Shapes

Angoli appena smussati (4px di base, 6px per i pannelli), come le linee di un campo. Le forme tonde sono riservate a ciò che è tondo davvero: chip dei filtri e dei convocati, badge, minuti degli eventi (pillola), pallone e cerchio di centrocampo. I segni del campo sono tratti di 2px nel bianco delle linee a opacità ridotta; la linea centrale del tabellino è tratteggiata.

## Components

### Buttons
- **Shape:** angoli smussati (4px), peso 600-650.
- **Primary:** di giorno verde campo con testo lime, di sera lime pieno con testo verde notte; su verde campo sempre lime.
- **Hover / Focus:** cambio di gradino di tono in 0.15s; premuto scende di 1px; anello di focus di 2px nel colore del focus del tema (verde di giorno, lime di sera) con 2px di distacco.
- **Outline:** bordo e testo nel colore d'accento, si riempie al passaggio.

### Chips
- **Style:** pillola con bordo d'accento (filtri) o filo nel colore della riga (convocati, con un puntino disegnato per chi non ha confermato).
- **State:** il filtro attivo prende il riempimento del pulsante primario.

### Cards / Containers
- **Corner Style:** 6px.
- **Background:** superficie del tema; piè di riquadro nella superficie rialzata con una riga sopra.
- **Shadow Strategy:** nessuna (vedi Elevation & Depth).
- **Internal Padding:** 1.25rem, 1.5-1.75rem da tablet.

### Inputs / Fields
- **Style:** fondo superficie, bordo nel colore della riga, angoli 4px, etichetta in peso 600 sopra il campo.
- **Focus:** anello morbido nel colore del focus del tema.
- **Error:** bordo e messaggio di Bootstrap sotto il campo, collegato con aria-describedby.

### Navigation
- **Barra superiore:** verde campo a strisce di taglio in entrambi i temi, marchio testuale "VALHALLA FC" condensato con "FC" in lime; voce attiva in lime con la linea di fondo lime da desktop.
- **Barra inferiore (mobile):** superficie del tema, icone Lucide; la voce attiva ha la sua linea in cima che scorre da una voce all'altra.

### Pitch Panel (signature)
Il pezzo di campo: verde campo, quattro strisce di taglio, lunetta o cerchio di centrocampo che entra dal bordo, testo nel bianco delle linee. Usato per la prossima partita, il bilancio, la home dei visitatori e la pagina non trovata. Nelle pagine di accesso è la metà campo in alto: il titolo sopra, il modulo sotto la linea di metà campo con il mezzo cerchio di centrocampo appoggiato.

### Scoresheet Pitch (signature)
Il tabellino come il campo visto dall'alto: il tempo scorre verso il basso lungo la linea tratteggiata centrale, la linea di metà campo con il cerchio è l'intervallo, a sinistra gli eventi nostri e a destra quelli avversari, con il minuto in una pillola bianca sulla linea.

### Empty State
Il cerchio di centrocampo con la linea di metà campo, disegnato in CSS a opacità ridotta (un campo ancora senza gioco), la frase che dice cosa manca in grassetto e, se serve, una riga su cosa succederà o un'azione. Variante compatta per riquadri piccoli e celle di tabella.

## Do's and Don'ts

### Do:
- **Do** usare il pezzo di campo per l'informazione che il giocatore cerca per prima, non come decorazione di ogni sezione.
- **Do** disegnare i segni del calcio in CSS (pallone, cartellini, cerchio di centrocampo) con tratti di 2px.
- **Do** tenere ogni stile nel foglio di stile: la CSP vieta stili inline e iniettati a runtime.
- **Do** colorare le superfici del browser dal tema: selezione lime, cursore e anello di focus nell'accento.
- **Do** rispettare "riduci movimento": le animazioni diventano istantanee.

### Don't:
- **Don't** usare il blu o il ciano di Bootstrap per azioni, selezioni o avvisi informativi.
- **Don't** mettere ombre grigie sotto i riquadri, bagliori o vetro.
- **Don't** usare il lime come fondo di grandi superfici o per testo lungo.
- **Don't** inventare stemmi, colori sociali o foto della squadra: non esistono.
- **Don't** mettere etichette sopra i titoli (kicker): il titolo parla da solo.
- **Don't** usare emoji al posto delle icone Lucide o dei segni disegnati.
