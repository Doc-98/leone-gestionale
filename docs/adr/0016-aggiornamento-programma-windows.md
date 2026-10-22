# 0016 · Aggiornamento automatico del programma per Windows

**Contesto.** Il programma per Windows è una finestra sul server (ADR 0006): l'interfaccia arriva dal server e si
aggiorna da sola. La parte nativa (pagina di avvio, comandi per lettore NFC e stampa) invece cambia solo con una nuova
installazione: finora bisognava scaricare l'installer dagli «Artifacts» della CI, o costruirlo con Rust, e reinstallarlo
a mano su ogni computer. Con più postazioni in società non è sostenibile.

**Decisione.**

- **Updater di Tauri** (`tauri-plugin-updater`): il programma legge
  `https://github.com/Doc-98/leone-gestionale/releases/latest/download/latest.json` e, se la versione è più alta,
  scarica l'installer `.msi`, lo installa (modalità «passive»: si vede l'avanzamento, Windows chiede il permesso di
  amministratore) e si riapre.
- **Pubblicazione dalla CI**: a ogni modifica di `desktop/` su `main` il workflow «Programma Windows» costruisce su
  Windows e crea una release `desktop-v0.2.<numero dell'esecuzione>` con gli installer e `latest.json` (con
  `tauri-action`). La versione cresce da sola: nessuno deve ricordarsi di cambiarla.
- **Firma**: gli aggiornamenti sono firmati (minisign) e il programma accetta solo quelli verificati con la chiave
  pubblica inclusa in `tauri.conf.json`. La chiave privata e la sua password sono segreti del repository GitHub, non
  stanno nel codice. Senza segreti la CI costruisce gli installer ma non pubblica aggiornamenti; le costruzioni in locale
  non firmano (`createUpdaterArtifacts` si accende solo nella CI).
- **Quando**: all'avvio, nella pagina iniziale del programma (al massimo 4 secondi, poi si va avanti anche senza
  internet), con «Aggiorna ora» / «Più tardi»; mentre si usa, con «Cerca aggiornamenti» nel menu e un avviso in cima
  alla pagina. Due comandi nativi (`cerca_aggiornamento`, `installa_aggiornamento`) chiamabili dalla pagina di avvio e
  dal server configurato, come gli altri.
- Il repository è pubblico, quindi le release si scaricano senza credenziali.

**Conseguenze.**

- Chi aveva una versione costruita a mano deve installare una volta quella della pagina delle release.
- Se la chiave privata si perde, i programmi installati non si aggiornano più da soli: vanno reinstallati con una
  chiave nuova. Va conservata fuori dal repository (gestore di password).
- Il programma non ha una firma del codice Authenticode: al primo download Windows SmartScreen avvisa. Una firma a
  pagamento si valuterà per la produzione.
- Se in società i PC non escono su internet, `latest.json` e gli installer si potranno servire dal server del
  gestionale (basta cambiare l'indirizzo nell'updater).
