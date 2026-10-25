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

I colori del campetto su cui la squadra gioca la sera: erba a strisce, linee bianche, casacche lime. È uno strumento da usare al telefono prima della partita, quindi conta capire subito lo stato delle cose; il carattere sta nei dettagli, non nelle decorazioni.

Le informazioni più importanti (prossima partita, bilancio, accesso, pagina non trovata) stanno su **pezzi di campo** verdi con le linee disegnate in CSS. Il resto vive su superfici sobrie. Il tabellino disegnato come il campo visto dall'alto è l'elemento più riconoscibile.

Da evitare: card bianche tutte uguali con accento blu, effetto videogioco, freddezza aziendale, bagliori e vetro.

**Key Characteristics:**
- Tema chiaro e scuro sulle variabili di Bootstrap 5.3, che seguono il dispositivo.
- Un solo carattere; la gerarchia la fanno peso e larghezza. Numeri sempre incolonnati.
- Colore solo dove significa qualcosa: stato, azione, squadra.
- Pallone, cartellini e linee disegnati in CSS, mai emoji.
- Leggero anche con poco segnale: niente immagini, font servito dal sito, animazioni brevi.

## Colors

Verdi dell'erba come gradini di tono, bianco delle linee per il testo sul campo, lime solo per azioni e selezioni.

### Primary
- **Verde campo** (turf-800, turf-700): pezzi di campo, barra in alto e pulsante principale di giorno; turf-700 anche per link e focus.
- **Verde notte** (turf-900, turf-950): pezzi di campo e barra in alto di sera; testo sopra il lime.

### Secondary
- **Lime casacca** (bib-lime, bib-lime-strong al passaggio): azioni, voce di menu attiva, vittorie e "disponibile" di sera, selezione del testo. Mai come sfondo di grandi superfici.

### Neutral
- **Bianco linea** (line-white): testo e linee sui pezzi di campo.
- **Terreno** (day-ground, night-ground): sfondo della pagina.
- **Superficie** e **superficie rialzata** (day/night-surface, day/night-surface-raised): riquadri, piè di riquadro, intestazioni di tabella.
- **Inchiostro**, **testo secondario** e **riga** (day/night-ink, -muted, -rule): testo, note, bordi.
- **Cartellini** (card-yellow, card-red): solo ammonizioni ed espulsioni.

### Named Rules
**The Bib Rule.** Il lime è la casacca: lo indossa solo ciò che si tocca o ciò che è selezionato. Su verde campo il pulsante principale è sempre lime.

**The Pitch Is Always Green Rule.** Un pezzo di campo è verde in entrambi i temi; di sera solo più scuro.

**The No Bootstrap Blue Rule.** Niente blu o ciano di Bootstrap: caselle, link, avvisi e pulsanti usano i colori del campo.

## Typography

**Display Font:** Archivo Variable (con system-ui, -apple-system, Segoe UI, Roboto)
**Body Font:** Archivo Variable (stesso file)

**Character:** un carattere sportivo usato in tutte le sue larghezze: stretto e pesante per titoli, avversari e punteggi, normale per il testo. Ricorda i numeri delle maglie.

### Hierarchy
- **Display** (820, clamp(2.25rem, 7vw, 3.25rem), 1.1; cifre a 860 e larghezza 70%): titoli di pagina, punteggi, totale da pagare, bilancio.
- **Headline** (760-840, 1.75rem, larghezza 72-78%, spesso maiuscolo): avversario e titoli dei pezzi di campo.
- **Title** (760, 1.25rem): titoli di sezione, orario della partita, importi.
- **Body** (400, 1rem, 1.5): testo e campi; 16px anche su mobile, così iOS non ingrandisce.
- **Label** (700, 0.8125rem, 0.04em, larghezza 85%, maiuscolo): squadre sul tabellone, sigle del bilancio, intestazioni del tabellino.

### Named Rules
**The Five Steps Rule.** Solo cinque dimensioni di testo: il resto lo fanno peso, larghezza e maiuscolo.

**The Tabular Rule.** Tutti i numeri sono tabulari, così punteggi, minuti e importi restano incolonnati.

## Layout

Si parte dal telefono: una colonna e la barra in basso con cinque sezioni; da 992px la barra sparisce e le voci tornano in alto. Le colonne si affiancano da 768px o 992px. Riquadri con 1.25rem di margine interno su mobile, 1.5-1.75rem da tablet. Su mobile le tabelle dello staff diventano schede e i filtri scorrono in orizzontale. Aree da toccare di almeno 44px. Su schermi larghi tabellino e punteggio restano larghi al massimo 48rem, le pagine di accesso 28rem.

## Elevation & Depth

Tutto piatto, senza ombre: i livelli si distinguono per tono (terreno, superficie, superficie rialzata) e per il verde dei pezzi di campo. Di sera i pezzi di campo hanno un filo sottile per staccarsi dallo sfondo scuro.

### Named Rules
**The Tonal Step Rule.** Un riquadro si stacca dallo sfondo con un tono diverso, non con un'ombra.

## Shapes

Angoli appena smussati: 4px, 6px per i pannelli. Tondo solo ciò che è tondo davvero: chip, badge, minuti degli eventi, pallone, cerchio di centrocampo. Le linee del campo sono tratti bianchi di 2px, semitrasparenti; quella centrale del tabellino è tratteggiata.

## Components

### Buttons
- **Shape:** angoli di 4px, testo in grassetto.
- **Primary:** di giorno verde con testo lime, di sera lime con testo verde scuro; su verde campo sempre lime.
- **Hover / Focus:** cambiano tono in 0.15s e scendono di 1px quando premuti; anello di focus di 2px, verde di giorno e lime di sera.
- **Outline:** bordo e testo nel colore d'accento, si riempie al passaggio.

### Chips
- **Style:** pillole con bordo; per i convocati un puntino segnala chi non ha confermato.
- **State:** il filtro attivo è pieno come il pulsante principale.

### Cards / Containers
- **Corner Style:** 6px.
- **Background:** superficie del tema; il piè di riquadro nella superficie rialzata.
- **Shadow Strategy:** nessuna.
- **Internal Padding:** 1.25rem, 1.5-1.75rem da tablet.

### Inputs / Fields
- **Style:** fondo superficie, bordo nel colore della riga, angoli 4px, etichetta sopra in grassetto leggero.
- **Focus:** anello morbido nel colore del tema.
- **Error:** bordo rosso e messaggio sotto il campo, letto anche dagli screen reader.

### Navigation
- **Barra in alto:** verde a strisce in entrambi i temi, scritta "VALHALLA FC" con "FC" in lime; voce attiva in lime, sottolineata da desktop.
- **Barra in basso (mobile):** icone Lucide; una linea sopra la voce attiva scorre da una voce all'altra.

### Pitch Panel (signature)
Il pezzo di campo: verde, quattro strisce, un arco di cerchio che entra dal bordo, testo bianco. Si usa per prossima partita, bilancio, home dei visitatori e pagina non trovata. Nell'accesso è una metà campo: titolo sopra, modulo sotto la linea con il mezzo cerchio di centrocampo.

### Scoresheet Pitch (signature)
Il tabellino come il campo visto dall'alto: il tempo scende lungo la linea centrale, la metà campo è l'intervallo, a sinistra i nostri eventi e a destra quelli avversari, con il minuto in una pillola bianca.

### Empty State
Il cerchio di centrocampo semitrasparente (un campo senza gioco), una frase in grassetto su cosa manca e, se serve, cosa succederà o un'azione. C'è una versione compatta per riquadri piccoli e tabelle.

## Do's and Don'ts

### Do:
- **Do** usare il pezzo di campo per ciò che il giocatore cerca per primo, non per decorare ogni sezione.
- **Do** disegnare pallone, cartellini e linee in CSS, con tratti di 2px.
- **Do** mettere ogni stile nel foglio di stile: la CSP blocca gli stili scritti nella pagina.
- **Do** colorare con il tema anche selezione del testo, cursore e focus.
- **Do** spegnere le animazioni quando il dispositivo chiede meno movimento.

### Don't:
- **Don't** usare il blu o il ciano di Bootstrap.
- **Don't** usare ombre grigie, bagliori o vetro.
- **Don't** usare il lime per grandi sfondi o testi lunghi.
- **Don't** inventare stemmi, colori sociali o foto della squadra.
- **Don't** mettere etichette sopra i titoli.
- **Don't** usare emoji al posto di icone o segni disegnati.
