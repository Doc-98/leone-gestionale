# 0028 · Componenti comuni dell'interfaccia

**Stato:** accettata (blocco 6 finito: i sei componenti sono costruiti)

## Contesto

Il design system «Corsia» descrive fumetto «i», barra di pagina, elenco modificabile, menu «⋯», stato vuoto e schede,
ma ogni pagina li rifà a modo suo: due menu «⋯» scritti a mano (scheda cliente, persone in attesa), tre tablist
diversi, circa 120 stati vuoti («Caricamento…», «Nessuno.», `<p class="card vuoto">`), paragrafi di spiegazione al
posto del fumetto, il riordino dei metodi di pagamento con due chiamate per ogni scambio. Il titolo della pagina lo
disegna `App.svelte`, periodo e viste ogni pagina per conto suo. Non ci sono test dei componenti: Vitest prova solo
`src/lib/*.ts`.

C'è anche un nodo di dominio. L'elenco modificabile ha il cestino in ogni riga, ma la regola «niente si cancella»
voleva che le cose con storico (un metodo di pagamento già usato) si spegnessero soltanto.

## Decisione

- **Sei componenti comuni** in `frontend/src/components/`, accanto ad `Azione`, `Foglio` e `Conferma`:
  `StatoVuoto`, `Fumetto`, `MenuAltro`, `Schede`, `BarraPagina` (con `Periodo` e `SceltaVista`), `ElencoModificabile`
  (con `RiquadroAcceso`). Le pagine non riscrivono più questi pezzi.
- **La logica sta in `src/lib/`**, in funzioni pure con i loro test (`posiziona`, `menu`, `schede`, `periodo`,
  `riordina`): posizione delle bolle, voce dopo un tasto, periodo precedente e successivo, nuovo ordine dopo un
  trascinamento. Il componente fa solo markup, eventi e stato.
- **Test dei componenti con jsdom e `@testing-library/svelte`** (dipendenze di sviluppo): si provano focus, Esc, frecce,
  Tab e i due tocchi della conferma. Le prove a mano del `qa-visuale` restano per tocco, trascinamento e aspetto.
- **Bolle e menu escono dalle card**: un portale li porta in `<body>` (le card con `overflow: hidden` non li tagliano)
  e restano dentro la schermata, a 16px dal bordo. Ne sta aperto uno solo per volta.
- **La barra di pagina la disegna la pagina**, non `App.svelte`, perché periodo, vista e azioni sono stato della
  pagina. `App.svelte` lascia l'intestazione alle pagine che la usano (campo `barra` in `SEZIONI`, finché tutte le
  pagine di lavoro non sono passate). Le pagine delle impostazioni tengono la loro intestazione con la ricerca
  (ADR 0024). La scritta del periodo apre il calendario nativo del sistema (`<input type="date">` o `type="month"`).
- **«Cambia password» ed «Esci»** stanno nel menu «Altro» sul telefono e nel riquadro dell'account in fondo alla barra
  laterale sul computer, non nella barra di pagina. La barra in basso del telefono si costruisce nel blocco 8; fino ad
  allora il tablet si comporta come il telefono (limite di 1024px del `Foglio`).
- **Menu «⋯»**: al massimo sette voci, separatori ammessi tra gruppi. L'azione che elimina è l'ultima, rossa, con i due
  tocchi dentro la voce. Un'azione negativa che apre un modulo («Banna la persona…») è anch'essa rossa e ultima, ma
  senza i due tocchi: il modulo è la conferma.
- **La sola lettura è una prop** (`modificabile`): chi non può modificare non vede maniglia, interruttore, cestino né
  «Aggiungi». Il controllo vero resta nel backend.
- **Elenco vuoto**: «Nessun X.» senza pulsante, poi la riga tratteggiata «Aggiungi un X». Con i puntini («Aggiungi un
  X…») quando apre un foglio.
- **Ogni elenco riordinabile salva l'ordine con una chiamata**: `PUT …/ordine` con `{"ids": [...]}`, in una
  transazione, come le postazioni. Il frontend aggiorna subito e torna all'ordine di prima se la chiamata fallisce.
- **Cestino su tutte le righe, eliminazione vera.** Regola generale per le cose con storico:
  - si eliminano davvero solo se lo storico ne conserva una **copia** presa al momento della registrazione (per un
    metodo di pagamento: nome e tipo, cioè cassetto, da incassare, riscontro POS, borsellino), e lo storico legge la
    copia;
  - finché c'è qualcosa di **aperto** che le usa, l'eliminazione è bloccata con un messaggio che dice cosa manca
    e cosa si può fare invece, su due righe separate da `\n`, ognuna con l'attacco in grassetto («**Non si può eliminare:**
    «Bonifico» ha ancora 57 pagamenti da incassare.» / «**Puoi spegnerlo:** …»; `lib/blocco.ts`);
  - niente stato «archiviato»: o la cosa c'è (accesa o spenta) o non c'è più;
  - le cose che il sistema stesso richiede non si eliminano mai (il borsellino) e la loro riga non ha il cestino.

  Per il resto il cestino non si nasconde: c'è sempre, e il blocco per qualcosa di aperto arriva con il messaggio al
  secondo tocco.

## Conseguenze

- Il blocco 6 costruisce i componenti con poche pagine pilota (Account, Phon e docce, Impostazioni › Cassa, scheda
  cliente, persone in attesa, Account e ruoli, I miei turni, Ore, Metodi di pagamento). Il blocco 7 li applica
  pagina per pagina e toglie i duplicati: `.menu-azioni`, `tastiSchede`, `.tab-c`, le rotelle delle viste, `Settimana`
  e `Mese` se restano senza uso, l'icona `spegni` nelle righe.
- `App.svelte` perde la sua intestazione quando l'ultima pagina di lavoro passa alla barra.
- Il principio «Niente si cancella» è riformulato in `docs/regole-dominio.md` come «Lo storico non si perde»: le cose che durano si chiudono con una data di fine; una cosa di cui lo storico conserva una copia presa alla registrazione si può eliminare.
- Il cestino che può rispondere «non ancora» è un'eccezione voluta alla regola «disabilitato solo quando manca qualcosa
  adesso»: il messaggio dice proprio cosa manca adesso.
- Due dipendenze di sviluppo in più nel frontend e un ambiente jsdom per i test dei componenti.
- L'ADR 0019 resta valido; tra i gesti ricorrenti «spegni» nelle righe degli elenchi diventa l'interruttore.
