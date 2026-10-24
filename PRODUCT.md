# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Giocatori** di Valhalla FC, squadra amatoriale di calcetto: usano soprattutto il telefono per rispondere alla disponibilità, vedere convocazioni, risultati, statistiche e le proprie quote da pagare.
- **Allenatore**: prepara e pubblica le convocazioni guardando le disponibilità della squadra; usa telefono e computer.
- **Admin**: gestisce utenti, calendario, risultati e tabellini, pagamenti e invii email, spesso dal computer.

## Product Purpose

Gestionale interno di Valhalla FC: calendario delle partite, disponibilità, convocazioni con avvisi email, risultati e tabellini, statistiche dei giocatori, pagamenti manuali delle quote. Successo: ogni giocatore sa in pochi secondi quando si gioca, se è convocato e se deve pagare qualcosa; lo staff organizza la partita senza chat e fogli di calcolo.

## Positioning

È lo strumento della squadra, non un prodotto commerciale: ruoli e regole reali di una squadra amatoriale (convocazioni tracciate e revisionabili, tabellino con controllo di coerenza, pagamenti confermati a mano dall'Admin, audit di ogni modifica).

## Operating Context

- Accesso solo per utenti registrati e approvati dall'Admin; ruoli cumulativi Giocatore, Allenatore, Admin.
- Uso tipico dal telefono nei giorni e nelle ore prima della partita; lo staff lavora anche da PC.
- Installabile come PWA; avvisi via email (Mailjet), nessuna notifica push.
- Fuso orario della squadra: Europe/Rome; interfaccia in italiano.

## Capabilities and Constraints

- Frontend React 19 + Vite + Bootstrap 5.3 (base mantenuta), Motion e componenti React Bits ammessi; altre dipendenze solo se essenziali.
- Content Security Policy rigida (`frontend/security-headers.js`): niente script o stili inline, niente stili iniettati a runtime, niente CDN esterni.
- Deve restare **veloce anche con rete lenta** (campi con poco segnale): poche risorse pesanti, animazioni sobrie, app shell leggera per la PWA.
- Mobile-first con barra di navigazione inferiore su smartphone; logica e divisione delle pagine esistenti da preservare.
- Tema chiaro e scuro con interruttore, che rispetta la preferenza del dispositivo.

## Brand Commitments

- Nome della squadra: **Valhalla FC**. Nessuno stemma né colori sociali definiti al momento.
- Verde campo come colore guida, accento lime (decisione dell'utente).

## Evidence on Hand

Nessuna foto, stemma o immagine della squadra disponibile: non inventarli. I dati mostrati sono quelli reali dell'app (partite, risultati, statistiche).

## Product Principles

- Prima l'azione del giocatore: rispondere, sapere se si è convocati, pagare.
- Chiarezza dello stato: ogni partita, convocazione e quota dice in che stato è.
- Leggero e veloce prima che spettacolare.
- Lo staff deve poter correggere tutto, con traccia di ogni modifica.

## Accessibility & Inclusion

Rispettare WCAG 2.1 AA di base (contrasto, focus visibile, aree di tocco ≥ 44px), `prefers-reduced-motion`.
