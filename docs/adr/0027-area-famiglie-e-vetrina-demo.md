# 0027 · L'app delle famiglie accanto al gestionale, pubblicata da sola, e la vetrina della demo

**Contesto.** L'ADR 0026 porta un'area per le famiglie: registrazione, richieste, posto offerto, pagamento, ricevute.
Le schermate sono state disegnate (mockup «Iscrizioni», tavole B1–B8) per telefono, tablet e computer. Restano da
decidere tre cose:

- dove vive il codice, visto che gestionale e app delle famiglie sono due programmi per persone diverse;
- come si arriva all'app da internet, con il server della società nella struttura (ADR 0006) e il gestionale che
  deve restare nella rete interna;
- come si presenta la demo, che ora deve mostrare cinque cose: gestionale da computer e da telefono, app delle
  famiglie da telefono, tablet e computer.

**Decisione.**

- **Stesso repository, cartelle separate.** Il frontend ha più pagine di ingresso nello stesso progetto Vite:
  - `index.html`: il gestionale, come oggi (lo apre anche il programma per Windows);
  - `famiglie.html`: l'app delle famiglie, con il suo manifest (nome e icona propri, si installa come app a sé);
  - `demo.html`: la vetrina, solo in sviluppo e nella demo (non entra nella build di produzione).

  In comune restano stili, colori e funzioni di `src/lib`; le schermate delle famiglie stanno in `src/famiglie/` e
  non importano quelle del gestionale. Il gestionale per ora non si sposta di cartella.
- **Nel backend un modulo `famiglie`**, nello stesso programma Spring Boot: endpoint solo sotto `/api/famiglie/**`,
  catena di sicurezza separata, limiti ai tentativi di accesso e di registrazione. Un account famiglia non ha
  nessun permesso del gestionale; un account dello staff non usa le API delle famiglie.
- **Due account.** Un genitore che lavora anche nella società ha due accessi distinti, uno per ruolo.
- **Pubblicazione: stesso server, un solo indirizzo verso l'esterno.** L'app delle famiglie gira sul server della
  società, che ha i dati aggiornati. Per raggiungerla da internet si usa un **tunnel in uscita** (es. Cloudflare
  Tunnel): nessuna porta aperta sul router della struttura, HTTPS e una protezione di base dagli attacchi. Il tunnel
  pubblica un solo nome (es. `famiglie.<dominio della società>`) e porta solo a `famiglie.html` e a
  `/api/famiglie/**`; il gestionale e le sue API restano nella rete interna. Da concordare con il tecnico, insieme a
  backup e aggiornamenti.
- **La vetrina della demo.** `avvia.cmd` apre `demo.html`: cinque schede (gestionale computer e telefono, famiglie
  telefono, tablet e computer). Le versioni da computer occupano la finestra, quelle da telefono e tablet stanno in
  una cornice della misura giusta (telefono 390×844, tablet 834×1194, ruotabile a 1194×834), rimpicciolita se serve.
  Una barra in alto resta sempre visibile e **cambia versione in qualsiasi momento**, senza riavviare niente; la
  versione scelta resta nell'indirizzo, così un ricaricamento riapre la stessa. Per ogni versione c'è il QR per aprirla
  sul telefono vero nella stessa rete, e un accesso rapido con gli account demo.

**Conseguenze.**

- La disposizione del gestionale (desktop sopra i 1024 px) segue la larghezza della cornice: la versione «telefono»
  della vetrina è il gestionale vero, non una copia.
- Per il QR il server di sviluppo ascolta anche nella rete locale (`vite --host`): `avvia.cmd` lo avvia così. Va bene in
  una rete di casa o della società, non in una rete pubblica.
- Se la connessione o la corrente della struttura cadono, l'app delle famiglie non è raggiungibile. Se un giorno
  servisse più continuità, si può spostare il solo tunnel o un proxy in cloud senza spostare i dati.
- Le schede delle famiglie nella vetrina restano «in arrivo» finché l'app non c'è (parte B dell'ADR 0026).
