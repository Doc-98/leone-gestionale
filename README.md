# Leone Gestionale

Gestionale per la società sportiva Leone XIII: si parte dalla piscina (turni dello staff, piano vasca,
clienti e accessi ai tornelli), con una struttura pronta per altri sport.

Architettura e decisioni: [`docs/architettura.md`](docs/architettura.md) · [`docs/adr/`](docs/adr/).

## Stato

**Fase 1 – turni dello staff (completa).** Una sola interfaccia in due forme: **programma per Windows** per gli
uffici (menu laterale, griglie ampie) e **app** sul telefono per lo staff (ADR 0006).

- *Staff*: i propri turni per settimana; da un turno si chiede un cambio orario, una sostituzione (a un collega
  o aperta a tutti) o uno scambio; «Turni da coprire» per offrirsi; ore e compenso del mese.
- *Coordinatore*: pianificazione della settimana a griglia (postazioni × giorni) con i turni scoperti in
  evidenza e le ore per persona; settimana tipo modificabile (anche copiandola per una nuova stagione) e
  generazione del mese; approvazione delle richieste; mansioni, postazioni e tariffe orarie; ore e compensi
  di tutto lo staff, in CSV e come **foglio ore nel modello Excel della società** (uno per persona o tutti in .zip).
- Garanzie nel database: nessun turno sovrapposto per la stessa persona, una sola richiesta aperta per turno.

**Fase 2 – piano vasca (completa).** Il giorno per fasce, con la pianta delle vasche (cosa c'è in ogni corsia, vasca
grande a 5 o 6 corsie, vasca piccola), la settimana come tabella fasce × giorni con i corsi di ogni fascia, la
griglia oraria come alternativa. Fasce definite dai coordinatori con interruttore 5/6 corsie; iscrizioni ai turni dei
corsi con posti (conteggio «7/8» nel piano), lista degli iscritti con le presenze per l'istruttore, iscrizioni e
ritiri per la segreteria.
Corsi con lezioni settimanali, salti e spostamenti di una singola data, eventi (gare, lezioni private e di prova,
chiusure anche di più giorni) e sovrapposizioni segnalate. Lo staff lo consulta; coordinatori e admin lo modificano.

**Fase 3 – clienti e tornello (in corso).** Al tornello si legge il braccialetto (NFC dal telefono Android, lettore
da scrivania o codice scritto) e si vede subito **nome ed età** di chi lo porta e **tutti i motivi** del rosso, ognuno
con cosa fare: braccialetto sconosciuto, smarrito, disattivato o bannato, persona bannata, certificato medico mancante o
scaduto, tesseramento annuale mancante o scaduto, abbonamento mancante, scaduto o non iniziato, carnet esaurito, fuori
dalla fascia del proprio turno (ADR 0009). Ogni caso è un controllo indipendente, attivabile e con i suoi parametri
nella pagina «Regole del tornello». Golden pass per dipendenti e tornellisti. Carnet che scala al primo passaggio e non
nei 90 minuti di rientro, a entrata libera o legato agli orari di un corso (es. nuoto libero). Ruoli modificabili con i
loro permessi (ADR 0010). La segreteria ha la scheda cliente: braccialetti (più di uno per persona), ban, golden pass,
certificato, tesseramento, abbonamenti, turni frequentati, ultimi passaggi e come andrebbe adesso al tornello.

## Struttura

```
backend/    Java 21 + Spring Boot 4 · un pacchetto per modulo (identita, struttura, staff, …)
frontend/   Svelte 5 + TypeScript + Vite · design system «Corsia» (da ClockWork)
desktop/    programma per Windows (Tauri 2): una finestra sul server, con accesso all'hardware
docs/       architettura e decisioni (ADR)
infra/      docker-compose per il database di sviluppo
```

## Avviare in locale

Questa parte spiega come far girare tutto il gestionale sul proprio computer, con dati inventati: database, server
(backend) e interfaccia (frontend). I comandi sono per **PowerShell su Windows**; dove su macOS/Linux cambiano, c'è
anche la versione per il terminale di quei sistemi.

In breve, quando è tutto pronto:

| Cosa | Dove gira | Come si avvia |
|---|---|---|
| Database (PostgreSQL) | in Docker, porta 5432 | `docker compose -f infra/docker-compose.yml up -d` |
| Backend (Java, Spring Boot) | http://localhost:8080 | `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"` in `backend` |
| Frontend (Svelte, Vite) | http://localhost:5173 | `npm run dev` in `frontend` |

### 0. Installare i programmi necessari (una volta sola)

| Programma | A cosa serve | Installazione su Windows (PowerShell) | Controllo |
|---|---|---|---|
| Git | scaricare il codice | `winget install Git.Git` | `git --version` |
| Java 21 (JDK) | far girare il backend | `winget install EclipseAdoptium.Temurin.21.JDK` | `java -version` → 21 |
| Node.js 22 o più recente | far girare il frontend | `winget install OpenJS.NodeJS.LTS` | `node -v` |
| Docker Desktop | il database (e i test del backend) | `winget install Docker.DockerDesktop` | `docker --version` |

Maven **non serve installarlo**: nella cartella `backend` c'è il Maven Wrapper (`mvnw.cmd` su Windows, `mvnw` su
macOS/Linux), che al primo avvio scarica da solo la versione giusta (3.9.11). Se hai già un Maven tuo va bene lo stesso,
ma usa la 3.9: il `4.0.0` scritto nel `pom.xml` è la versione del formato del file, non di Maven.

Dopo aver installato i programmi **chiudi e riapri PowerShell**, così trova i comandi nuovi. Docker Desktop va aperto
almeno una volta (e deve restare acceso mentre lavori): la prima volta chiede di accettare le condizioni e può chiedere
di riavviare il computer.

Su macOS si installa tutto con [Homebrew](https://brew.sh): `brew install git node temurin@21` e `brew install --cask docker`.

### 1. Scaricare il codice

Scegli una cartella dove tenere i progetti (es. `Documenti\GitRepos`), poi:

```powershell
cd $HOME\Documents\GitRepos          # o la cartella che preferisci
git clone https://github.com/Doc-98/leone-gestionale.git
cd leone-gestionale
```

Da qui in poi i comandi partono sempre da questa cartella, `leone-gestionale` (la «cartella del progetto»).

### 2. Avviare il database

Con Docker Desktop acceso, dalla cartella del progetto:

```powershell
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps     # deve dire «healthy» dopo qualche secondo
```

Il database resta acceso in sottofondo anche se chiudi PowerShell (si ferma con il passo «Fermare tutto», più sotto).
I dati restano tra un avvio e l'altro.

### 3. Avviare il backend

In una finestra di PowerShell:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Su macOS/Linux: `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`.

- Le **virgolette** attorno a `-Dspring-boot.run.profiles=demo` servono in PowerShell: senza, PowerShell spezza
  l'argomento al punto e Maven risponde «Unknown lifecycle phase».
- Il profilo `demo` riempie il database con persone, corsi, turni e braccialetti **inventati** (la prima volta ci mette
  un po'). Senza profilo il database resta vuoto.
- È pronto quando compare `Started GestionaleApplication`. Lascia questa finestra aperta: chiudendola si ferma il
  backend (oppure `Ctrl+C`).
- Prova: http://localhost:8080/actuator/health deve rispondere `{"status":"UP"}`.

### 4. Avviare il frontend

In una **seconda** finestra di PowerShell, dalla cartella del progetto:

```powershell
cd frontend
npm install        # solo la prima volta e quando cambiano le dipendenze
npm run dev
```

Lascia aperta anche questa finestra. Le modifiche al codice del frontend si vedono subito nel browser, senza riavviare.

### 5. Aprire il gestionale

Apri http://localhost:5173 ed entra con uno degli account demo (vedi «Provare il gestionale», sotto), ad esempio
`segreteria@demo.leone` con password `demo1234`.

### Aggiornare all'ultima versione

Quando su GitHub c'è codice nuovo:

```powershell
git pull                              # dalla cartella del progetto
cd frontend; npm install; cd ..       # se sono cambiate le dipendenze del frontend (non fa danni farlo sempre)
```

Poi riavvia il backend (`Ctrl+C` nella sua finestra e di nuovo il comando del passo 3): le modifiche al database si
applicano da sole all'avvio. Il frontend si aggiorna da solo.

### Fermare tutto, o ripartire da zero

```powershell
# backend e frontend: Ctrl+C nelle loro finestre
docker compose -f infra/docker-compose.yml stop        # ferma il database (i dati restano)
docker compose -f infra/docker-compose.yml down -v     # cancella il database: al prossimo avvio i dati demo ripartono da capo
```

Ripartire da zero serve, ad esempio, dopo aver fatto molte prove: con `down -v` e di nuovo i passi 2 e 3 si ritrovano i
dati demo come all'inizio (le date si ricalcolano su oggi).

### Dati veri solo sul tuo computer (profilo «locale»)

Alcuni dati veri (per esempio i turni dello staff reale degli spogliatoi) non devono finire su GitHub. Per provarli:

1. Crea la cartella `backend\dati-locali` e mettici dentro i file `.sql` (es. `R__staff_spogliatoi_reale.sql`).
   La cartella è esclusa da git: `git status` non la mostra mai.
2. Avvia il backend con il profilo `locale` oltre a `demo`:

   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo,locale"
   ```

   I file si applicano dopo i dati demo. Un file `R__…` si riapplica da solo al riavvio quando lo modifichi.
3. Per tornare ai soli dati inventati: `docker compose -f infra/docker-compose.yml down -v` e avvio con il solo `demo`.

I test usano solo il profilo `demo`: i dati della cartella non entrano mai nei test.

### Problemi frequenti

| Messaggio | Causa e soluzione |
|---|---|
| `Unknown lifecycle phase ".run.profiles=demo"` | Mancano le virgolette attorno a `-Dspring-boot...` in PowerShell (passo 3). |
| `Connection to localhost:5432 refused` all'avvio del backend | Il database non è acceso: apri Docker Desktop e ripeti il passo 2. |
| `port is already allocated` (5432) al passo 2 | C'è già un PostgreSQL installato sul computer che usa la porta: fermalo, oppure cambia `"5432:5432"` in `"5433:5432"` in `infra/docker-compose.yml` e avvia il backend con `$env:DB_URL="jdbc:postgresql://localhost:5433/gestionale"` prima del comando. |
| `JAVA_HOME is not set` o Java sbagliato | `java -version` deve dire 21. Imposta la variabile: `$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21…"` (il percorso del tuo JDK). |
| Il frontend si apre ma ogni pagina dà errore | Il backend non è avviato o non è ancora pronto (passo 3). |
| `npm` non è riconosciuto | Node non è installato o PowerShell non è stato riaperto dopo l'installazione. |

## Provare il gestionale (dati demo)

Account demo (password `demo1234`): `admin@demo.leone`, `coordinatore@demo.leone`, `segreteria@demo.leone`,
`staff@demo.leone` (Luca, spogliatoi), `giulia@demo.leone`, `marta@demo.leone`; istruttori come
`federico.mauri@demo.leone` (Delfino Giallo) e `elena@demo.leone` (Acquababy, AquaFitness); lo staff degli spogliatoi
come `ruggero.sala@demo.leone`. Sono persone inventate: nello sviluppo non si usano dati reali.

- **Piano vasca come sul sito**: tutti i turni di tutti i corsi della pagina «Piscina» della Leone XIII Sport (dall'Acquababy
  all'AquaFitness, con nuoto adulti, corsi leoniani e nuoto libero), un corso per corsia, la vasca grande a 6 corsie e il
  mercoledì a 5. Ogni turno ha i suoi iscritti inventati; gli istruttori sono inventati, tranne i responsabili indicati sul
  sito per Propaganda e corsi leoniani.
- **Staff degli spogliatoi** organizzato come il foglio turni vero (maschile a 3 postazioni, femminile a 2, piccoli fino
  a 6, atrio, domenica mattina), con persone inventate, un modello settimanale, turni scoperti e richieste aperte.

Braccialetti demo da provare al tornello (si scrivono nel campo): `D0000001` carnet (verde, scala), `D0000002` Delfino
Giallo bisettimanale (verde solo nella fascia), `D0000003` certificato scaduto e tesseramento mancante, `D0000004`
smarrito (`D0000014` è il suo nuovo), `D0000005` braccialetto bannato, `D0000006` persona bannata, `D0000007` carnet
esaurito, `D0000008` abbonamento scaduto, `D0000009` nessun abbonamento, `D0000104` Giulia con golden pass,
`D0000010` carnet negli orari del nuoto libero, `D0000012` 14 anni con certificato provvisorio e permesso di
uscita autonoma (prova il lato «Uscita»), `FFFFFFFF` sconosciuto. Sofia (`D0000001`) è «sotto controllo»: la segreteria
riceve un avviso. Anche il codice decimale del vecchio gestionale funziona (es. `3489660929` = `D0000001`).

- **Scadenze e promemoria** (ADR 0012, 0013): pagina «Scadenze» (certificati, abbonamenti, tesseramento mancante) e
  promemoria email automatici con testi modificabili; senza server di posta le email si registrano ma non partono.
  Account dello staff con password temporanea.
- **Scheda cliente completa** (ADR 0014): anagrafica con codice fiscale (anche nella ricerca), tutori con «riceve le
  comunicazioni» e «può venire a prenderlo», fratelli, consensi privacy. Prova Tommaso Neri (`D0000002`): la madre
  Paola riceve i promemoria e la sorella Sara compare tra i fratelli.
- **Liste d'attesa e cambio di turno** (ADR 0015): pagina «Liste d'attesa» (in un turno si libera un posto tra 6
  giorni: avvisa il primo e iscrivilo), lista d'attesa anche negli iscritti del piano vasca e nella scheda (Sara Neri è
  prima in lista); dalla scheda si cambia turno scegliendo tra i turni del corso con i posti.
- **Braccialetti nel formato dei chip NFC** (seriali di 4 e 7 byte), per le prove con il telefono: cerca in «Clienti»
  Chiara Venturi, Davide Colombi, Elisa Marchi, Federico Bernardi o Giada Rinaldi (verdi e rossi per motivi diversi).
- **Impostazioni della società** (admin, ADR 0018): dati della società, stagioni con la quota associativa (demo: dal 1/9
  al 31/8, 35 €), gruppi di clienti (demo: «Alunni dell'Istituto», «Squadra agonistica»); i gruppi si danno dalla scheda.
- **Listino e regole di prezzo** (ADR 0018): «Impostazioni → Listino e prezzi» con i prezzi della Leone (scuola nuoto mono 520 €, bi 795 €,
  alunni dell'Istituto 460 €, nuoto libero, carnet) e le regole (riduzione per iscrizione tardiva, rate, recesso).
  Nella scheda, «Nuovo abbonamento o carnet…» vende dal listino con l'importo proposto; cambiarlo richiede il permesso
  «Cambiare l'importo proposto» e un motivo. «Termina oggi» mostra il rimborso secondo la regola di recesso.
- **Addebiti e pagamenti** (ADR 0018, parte 5): nella scheda, «Pagamenti» mostra cosa c'è da pagare e entro quando
  (quota, rate, altri importi), i familiari con qualcosa da pagare e lo storico. «Registra pagamento…»: chi paga (anche
  il genitore, per più figli insieme), uno o più metodi (parte contanti, parte carta), anche un acconto. Il bonifico
  resta «da incassare» finché non si segna arrivato. Pagina «Incassi» con i totali per metodo, l'esportazione CSV e i
  bonifici da incassare; «Impostazioni → Metodi di pagamento». Annullare pagamenti e addebiti richiede il permesso
  «Annullare pagamenti» (demo: coordinatore) e il motivo; acconti e pagamenti divisi tra più metodi il permesso «Acconti
  e pagamenti misti». Per due fratelli insieme: un pagamento unico, uno per ciascuno o a scelta, secondo «Impostazioni →
  Società e stagioni». Prova Tommaso Neri (`D0000002`): la seconda rata è scaduta,
  la paga la mamma. Con «Termina oggi» e il recesso, le rate non pagate si annullano e il rimborso diventa un credito.
- **Impostazioni** (società e stagioni, listino, promemoria, regole del tornello, mansioni e tariffe, account, ruoli): tutte
  sotto la voce «Impostazioni» in fondo alla barra laterale, che scorre e mostra l’elenco delle impostazioni per area
  («← Impostazioni» torna indietro); sul telefono da «Altro».
- La segreteria ha di partenza tutta la gestione; ruoli e permessi si cambiano dalla pagina «Ruoli e permessi» (admin).

## Provare l'NFC dal telefono

Il gestionale legge i braccialetti con l'NFC del telefono direttamente dal browser (Web NFC, ADR 0004): pagina
«Tornello» → «Leggi i braccialetti con l'NFC», oppure il pulsante «NFC» accanto a «Assegna un braccialetto» nella scheda
del cliente. Funziona solo con **Chrome su Android** e solo su indirizzi **sicuri** (https, oppure `localhost`).
In sviluppo il telefono apre il gestionale dall'indirizzo del PC in rete (`http://192.168…`), che non è sicuro: si
segna come sicuro una volta in Chrome. Passo passo:

1. Telefono e PC sulla **stessa rete Wi-Fi**; sul telefono l'NFC acceso (Impostazioni → Dispositivi connessi → NFC).
2. Avvia database e backend come sopra; il frontend invece avvialo così, perché sia raggiungibile dalla rete:

   ```powershell
   cd frontend
   npm run dev:telefono
   ```

   Vite scrive due indirizzi: usa quello «Network», ad esempio `http://192.168.1.50:5173`. (Lo trovi anche con
   `ipconfig`, alla voce «Indirizzo IPv4» della scheda Wi-Fi.) La prima volta Windows chiede se consentire a Node.js
   l'accesso alla rete: consenti sulle **reti private**.
3. Sul telefono, in Chrome, apri `chrome://flags/#unsafely-treat-insecure-origin-as-secure`, scrivi nel riquadro
   l'indirizzo del punto 2 (es. `http://192.168.1.50:5173`), imposta **Enabled** e tocca **Relaunch**. Si fa una
   volta sola (finché non cambia l'indirizzo del PC).
4. Apri quell'indirizzo, entra come `segreteria@demo.leone` e vai su «Tornello» → «Leggi i braccialetti con l'NFC».
   Chrome chiede il permesso per l'NFC: consenti. Appoggia il braccialetto al retro del telefono.
5. Un braccialetto nuovo risulta «sconosciuto» e il tornello mostra il suo codice. Per collegarlo a una persona demo:
   «Clienti» → cerca la persona → «Assegna un braccialetto» → «NFC» → appoggia il braccialetto → «Assegna». Da lì il
   tornello lo riconosce.

Se il pulsante NFC non compare, sotto al campo del codice c'è scritto il motivo (browser, indirizzo non sicuro, NFC spento).

**Cosa si può leggere.** Va bene qualunque tag NFC comune (braccialetti, tessere, adesivi NTAG): il gestionale usa solo
il numero di serie del chip, che è fisso e diverso per ogni tag, quindi **non serve programmarli**. Un telefono invece
non può fingersi un braccialetto: quando emula una carta presenta un numero di serie casuale, diverso a ogni
avvicinamento. Per avere più «braccialetti» di prova bastano quindi degli adesivi o tessere NFC qualsiasi.

### Il telefono come lettore del PC

Dal PC (browser o programma per Windows) si può usare un telefono Android come lettore NFC (ADR 0017): i braccialetti
letti dal telefono arrivano al PC come da un lettore da scrivania. Il telefono non deve accedere e non vede dati dei
clienti: manda solo i codici.

1. Prepara il telefono come nei punti 1–3 qui sopra (frontend avviato con `npm run dev:telefono`, indirizzo del PC
   segnato come sicuro in Chrome).
2. Sul PC apri «Tornello» (o la scheda di un cliente, «Assegna un braccialetto») e premi **Collega un telefono**:
   compare un QR con un codice di 6 cifre.
3. Sul telefono inquadra il QR con la fotocamera (oppure apri `http://<indirizzo del PC>:5173/#/lettore` e scrivi il
   codice). Sul PC compare «Telefono collegato».
4. Sul telefono tocca **Inizia a leggere** e appoggia i braccialetti: sul PC il tornello mostra subito l'esito (nella
   scheda, il codice finisce nel campo del braccialetto).
5. Finito, «Scollega» sul PC (comunque l'abbinamento finisce da solo dopo qualche minuto senza il PC).

Al tornello e in segreteria i lettori fissi da scrivania che «scrivono» il codice come una tastiera funzionano già: basta
che il cursore sia nel campo del codice.

## Programma per Windows

Il programma (`desktop/`, fatto con Tauri) è **una finestra sul server**: al primo avvio chiede l'indirizzo del server,
poi apre l'interfaccia servita da lì. Per questo:

- le modifiche all'**interfaccia** (cartella `frontend`) arrivano da sole, senza reinstallare niente: basta ricaricare
  (in sviluppo Vite le applica subito);
- solo le modifiche alla **parte nativa** (cartella `desktop`) richiedono una versione nuova del programma, e quella
  arriva con l'**aggiornamento automatico** (ADR 0016).

### Installarlo (la prima volta)

1. Apri la pagina dell'ultima versione: https://github.com/Doc-98/leone-gestionale/releases/latest
2. In «Assets» scarica il file `Leone.Gestionale_…_x64_it-IT.msi` e aprilo.
3. Windows può mostrare «Windows ha protetto il PC» (il programma non ha ancora una firma digitale a pagamento): «Ulteriori
   informazioni» → «Esegui comunque». L'installazione chiede il permesso di amministratore.
4. Al primo avvio il programma chiede l'**indirizzo del server**. In sviluppo, con backend e frontend avviati come sopra,
   scrivi `http://localhost:5173`. Si cambia quando vuoi da «Cambia server», in fondo al menu laterale.

Se avevi installato una versione costruita da te prima che ci fosse l'aggiornamento automatico, disinstallala da
Impostazioni → App e installa quella della pagina sopra (una volta sola).

### Aggiornamenti

- **All'avvio** il programma controlla se c'è una versione nuova e, se c'è, propone «Aggiorna ora» (o «Più tardi»).
- **Mentre lo usi**: in fondo al menu laterale «Cerca aggiornamenti»; se c'è una versione nuova compare anche un
  avviso in cima alla pagina con «Aggiorna e riavvia».
- L'aggiornamento si scarica, si installa (Windows chiede il permesso di amministratore) e il programma si riapre da solo.

Le versioni nuove le pubblica la CI: a ogni modifica di `desktop/` su `main`, il workflow «Programma Windows» costruisce
gli installer su Windows e crea una release `desktop-v0.2.N` con il file `latest.json`, firmato, da cui i programmi
installati capiscono che c'è un aggiornamento. Per firmare servono due segreti del repository (vedi sotto).

### Configurare la pubblicazione (una volta sola, per chi gestisce il repository)

Gli aggiornamenti sono firmati: il programma installa solo versioni firmate con la chiave del progetto. La chiave
privata e la sua password **non stanno nel repository** (la chiave pubblica è in `desktop/src-tauri/tauri.conf.json`).

1. Su GitHub: repository → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**.
2. Nome `TAURI_SIGNING_PRIVATE_KEY`, valore: tutto il contenuto del file della chiave privata (`leone-gestionale.key`).
3. Nome `TAURI_SIGNING_PRIVATE_KEY_PASSWORD`, valore: la password della chiave.
4. **Actions** → «Programma Windows» → **Run workflow** per pubblicare subito la prima versione.

Conserva chiave e password in un posto sicuro (es. un gestore di password): se si perdono, i programmi installati non
possono più aggiornarsi da soli e vanno reinstallati a mano con una chiave nuova.

Senza i segreti la CI costruisce comunque gli installer, scaricabili dalla pagina dell'esecuzione (sezione «Artifacts»),
ma senza aggiornamento automatico.

### Costruirlo sul proprio computer (solo per chi modifica `desktop/`)

Serve, oltre a Node.js, il compilatore di Rust con gli strumenti di Microsoft per il C++:

```powershell
winget install Rustlang.Rustup
winget install Microsoft.VisualStudio.2022.BuildTools --override "--wait --passive --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"
```

Riapri PowerShell (controllo: `cargo --version`), poi dalla cartella del progetto:

```powershell
cd desktop
npm ci
npx tauri dev       # apre il programma in modalità sviluppo, si ricompila a ogni modifica
npx tauri build     # costruisce gli installer
```

Gli installer finiscono in `desktop\src-tauri\target\release\bundle\msi\` (e `...\nsis\` per l'`.exe`). Una versione
costruita così è la `0.1.0`: il programma la vede sempre come vecchia e propone l'ultima pubblicata.

Il server deve offrire interfaccia e `/api` **sullo stesso indirizzo** (in sviluppo lo fa Vite; in produzione un
proxy, da definire con il tecnico della società).

## Test

Prima di pubblicare una modifica si fanno girare i test di quello che si è toccato. La CI (GitHub Actions) li rifà
comunque a ogni push su GitHub: i risultati sono nella scheda **Actions** del repository.

### Backend

Serve **Docker Desktop acceso**: i test avviano da soli un PostgreSQL usa e getta in un container (Testcontainers),
quindi non toccano il database di sviluppo.

```powershell
cd backend
.\mvnw.cmd verify                              # tutti i test (qualche minuto)
.\mvnw.cmd test "-Dtest=AttesaTest"            # una sola classe di test
```

Su macOS/Linux: `./mvnw verify`. Alla fine deve comparire `BUILD SUCCESS`; se un test fallisce, Maven dice quale e
il dettaglio è in `backend\target\surefire-reports\`.

Senza Docker si può usare un PostgreSQL già installato, con un database vuoto dedicato (viene svuotato a ogni giro):

```powershell
$env:TEST_DB_ESTERNO="true"
$env:DB_URL="jdbc:postgresql://localhost:5432/gestionale_test"
$env:DB_USER="postgres"; $env:DB_PASSWORD="la-tua-password"
.\mvnw.cmd verify
```

Con il backend avviato in profilo `demo`, la documentazione interattiva delle API è su
http://localhost:8080/swagger-ui.html.

### Frontend

```powershell
cd frontend
npm run check      # controllo dei tipi (TypeScript e Svelte)
npm test           # test unitari (Vitest)
npm run build      # prova che l'app si costruisca per la produzione
```

### Programma per Windows

Se si tocca `desktop/`, almeno la compilazione: `cd desktop\src-tauri` e `cargo build`. Gli installer li costruisce
la CI su Windows.

## API

| Metodo | Percorso | Chi |
|---|---|---|
| GET | `/api/auth/csrf` | tutti: imposta il cookie `XSRF-TOKEN` |
| POST | `/api/auth/login` · `/api/auth/logout` | `{email, password}` → utente e ruoli |
| GET | `/api/me` | collegati |
| GET | `/api/turni/miei?da=&a=` | i propri turni (default: prossimi 30 giorni) |
| GET | `/api/turni/staff?da=&a=` · `/api/staff/colleghi` | turni e colleghi degli impianti in cui si lavora |
| GET | `/api/turni?da=&a=` | coordinatori: tutti i turni, anche scoperti |
| POST · PUT | `/api/turni` · `/api/turni/{id}` · `/api/turni/{id}/annulla` | coordinatori |
| GET | `/api/staff/postazioni` · `/api/staff/persone` | coordinatori |
| GET · POST · PUT · DELETE | `/api/modelli` · `/{id}` · `/{id}/righe` · `/{id}/righe/{riga}` · `/{id}/genera` `{mese}` | coordinatori: settimana tipo |
| GET | `/api/staff/impianti` · `/api/staff/spazi` | coordinatori |
| GET · POST · PUT | `/api/staff/mansioni` · `/{id}` · `/{id}/postazioni` · `/api/staff/postazioni/{id}` · `/api/staff/postazioni/ordine` | coordinatori |
| GET · POST · PUT | `/api/staff/tariffe` · `/{id}` | coordinatori: una nuova tariffa chiude la precedente |
| POST | `/api/richieste` | sui propri turni: `CAMBIO_ORARIO`, `SOSTITUZIONE`, `SCAMBIO` |
| GET | `/api/richieste/mie` · `/api/richieste/aperte` | le proprie / le sostituzioni aperte |
| POST | `/api/richieste/{id}/accetta` · `/rifiuta` · `/annulla` | collega / richiedente |
| GET · POST | `/api/richieste` · `/{id}/approva` · `/{id}/respingi` | coordinatori |
| GET | `/api/riepilogo/mio?mese=` | ore e compenso propri |
| GET | `/api/riepilogo?mese=` · `/api/riepilogo/csv?mese=` | coordinatori |
| GET | `/api/piano?da=&a=` | tutti: spazi e occorrenze del periodo (max due mesi), con stato e sovrapposizioni |
| GET · POST · PUT | `/api/corsi` · `/{id}` · `/{id}/lezioni` | lettura per tutti, modifiche coordinatori |
| PUT · DELETE | `/api/lezioni/{id}` · POST `/api/lezioni/{id}/eccezioni` · DELETE `/api/eccezioni/{id}` | coordinatori: salti e spostamenti |
| GET · POST · PUT | `/api/eventi/{id}` · `/api/eventi` · `/{id}/annulla` | lettura per tutti, modifiche coordinatori |
| GET · POST · PUT · DELETE | `/api/fasce` · `/{id}` · POST `/{id}/assetto` `{assetto}` | lettura per tutti, modifiche coordinatori |
| GET · POST | `/api/lezioni/{id}/iscritti?data=` | lista: istruttore del turno, coordinatori, segreteria; iscrive la segreteria |
| POST · PUT | `/api/iscrizioni/{id}/termina` · `/api/lezioni/{id}/presenze` `{data, presenze}` | ritiro (segreteria); presenze (istruttore) |
| GET | `/api/persone?q=` | ricerca nell'anagrafica (segreteria, coordinatori) |
| POST · GET | `/api/accessi/passaggi` `{codice}` · `?quanti=` | tutti: passaggio al tornello (esito, persona, motivi); passaggi di oggi |
| GET | `/api/accessi/persone/{id}` | segreteria: braccialetti, ban, golden pass, passaggi, esito «adesso» |
| POST · PUT | `/api/accessi/persone/{id}/braccialetti` · `/api/accessi/braccialetti/{id}/stato` | segreteria: assegna; ATTIVO, SMARRITO, DISATTIVATO |
| POST | `/api/accessi/ban` `{tipo: BAN \| SORVEGLIANZA, personaId \| braccialettoId, motivo, al}` · `/ban/{id}/revoca` | segreteria |
| POST | `/api/accessi/persone/{id}/autorizzazioni` · `/autorizzazioni/{id}/revoca` | permessi firmati: uscita autonoma, accompagnatore |
| GET | `/api/accessi/segnalazioni?dopo=` | passaggi di chi è sotto controllo (avvisi in segreteria) |
| POST | `/api/clienti/abbonamenti/{id}/sospensioni` · `/api/clienti/sospensioni/{id}/annulla` | sospensione di un abbonamento |
| POST | `/api/accessi/persone/{id}/golden-pass` · `/golden-pass/{id}/revoca` | coordinatori |
| GET · PUT | `/api/accessi/regole` · `/{codice}` `{attiva, parametri}` | lettura coordinatori, modifica admin |
| GET · POST · PUT | `/api/account` · `/{id}/password` · `/{id}/disattiva` · `/{id}/riattiva` · `/{id}/email` | account dello staff (permesso ACCOUNT) |
| PUT | `/api/me/password` `{attuale, nuova}` | la propria password |
| GET · POST | `/api/scadenze?entro=` · `/api/scadenze/promemoria` | scadenze; promemoria a mano (permesso CLIENTI) |
| GET · POST · PUT · DELETE | `/api/promemoria` · `/{id}` · `/esegui` | regole e testi dei promemoria, giro adesso |
| PUT | `/api/clienti/{id}/contatti` `{email, telefono}` | contatti del cliente |
| PUT | `/api/clienti/{id}/anagrafica` | anagrafica (codice fiscale controllato e unico) |
| POST · PUT | `/api/clienti/{id}/tutori` · `/api/clienti/legami/{id}` · `/legami/{id}/chiudi` | tutori: aggiungi (esistente o nuovo), modifica, togli |
| POST | `/api/clienti/{id}/consensi` · `/api/clienti/consensi/{id}/revoca` | consensi privacy |
| GET | `/api/attesa` · `/api/corsi/{id}/turni` · `/api/lezioni/{id}/attesa` | liste d'attesa; turni di un corso con posti e attesa |
| POST | `/api/lezioni/{id}/attesa` · `/api/attesa/{id}/iscrivi` · `/rinuncia` · `/avvisa` | lista d'attesa: aggiungi, iscrivi, togli, avvisa la famiglia |
| POST | `/api/iscrizioni/{id}/cambia` `{lezioneId, dal, forza}` | cambio di turno (anche di corso) da una data |
| POST · GET · DELETE | `/api/lettori` · `/{id}/letture?dopo=` · `/{id}` | il PC abbina un telefono come lettore e riceve i codici |
| POST · GET | `/api/lettori/telefono/{token}/presente` · `/telefono/{token}/letture` `{codice}` · `/api/lettori/codice/{codice}` | il telefono (senza accesso, con il token) |
| GET · PUT | `/api/societa` | dati della società (modifica: permesso IMPOSTAZIONI) |
| GET · POST · PUT · DELETE | `/api/stagioni` · `/{id}` | stagioni e quota associativa (modifica: IMPOSTAZIONI) |
| GET · POST · PUT | `/api/gruppi` · `/{id}` · `/{id}/persone` | gruppi di clienti (creazione: IMPOSTAZIONI) |
| POST | `/api/clienti/{id}/gruppi` `{gruppoId}` · `/api/clienti/appartenenze/{id}/chiudi` | mettere e togliere una persona da un gruppo (CLIENTI) |
| GET · POST · PUT · DELETE | `/api/listino?stagione=` · `/api/listino/offerte` · `/offerte/{id}` | listino della stagione (modifica: IMPOSTAZIONI) |
| PUT · POST | `/api/listino/regole/{stagione}/{tipo}` · `/api/listino/copia` `{da, a, aumentoPercentuale}` | regole di prezzo; copia del listino |
| GET | `/api/clienti/{id}/proposta?offertaId=&volte=&dal=` · `/api/clienti/abbonamenti/{id}/recesso?data=` | importo proposto; rimborso per recesso |
| POST | `/api/clienti/abbonamenti/{id}/termina` `{al, recesso}` | termina; con il recesso annulla le rate non pagate e accredita il rimborso |
| GET | `/api/pagamenti/persone/{id}` | conto: addebiti, pagamenti, famiglia, chi può pagare (CLIENTI o PAGAMENTI) |
| POST | `/api/pagamenti` `{paganteId, addebiti, metodi, importo?, data?, note?}` · `/api/pagamenti/addebiti` | pagamento (anche misto); addebito a mano (PAGAMENTI) |
| POST | `/api/pagamenti/{id}/annulla` · `/api/pagamenti/addebiti/{id}/annulla` `{motivo}` | annullamenti (PAGAMENTI_ANNULLA) |
| GET · POST | `/api/pagamenti/incassi?da=&a=` · `/api/pagamenti/da-incassare` · `/api/pagamenti/metodi-usati/{id}/incassa` | incassi e bonifici da incassare (PAGAMENTI) |
| GET · POST · PUT | `/api/pagamenti/metodi` · `/{id}` | metodi di pagamento (modifica: IMPOSTAZIONI) |
| GET · POST · PUT · DELETE | `/api/ruoli` · `/{codice}` · `/{codice}/assegnazioni` · `/api/assegnazioni/{id}` | ruoli e permessi (permesso RUOLI) |
| POST · GET | `/api/clienti` · `/{id}` | segreteria: nuovo cliente; scheda (registra la consultazione) |
| POST | `/api/clienti/{id}/certificati` · `/tesseramenti` · `/abbonamenti` · `/abbonamenti/{id}/termina` · `/{tipo}/{id}/annulla` | segreteria |
| GET | `/api/foglio-ore/mio?mese=` | il proprio foglio ore (.xlsx) |
| GET | `/api/foglio-ore/persone/{id}?mese=` · `/api/foglio-ore/tutti?mese=` · `/api/foglio-ore/voci` | coordinatori (.xlsx, .zip, colonne del modello) |

Le richieste che modificano dati devono rimandare il token CSRF nell'intestazione `X-XSRF-TOKEN`.
Gli errori seguono il formato «problem details» (RFC 9457): 400 dati non validi, 403 permessi,
404 non trovato, 409 conflitto (es. turni sovrapposti).

## Posta (promemoria)

Il server di posta si configura con le variabili standard di Spring: `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`,
`SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true`; il mittente con
`GESTIONALE_POSTA_MITTENTE`, l'orario del giro con `GESTIONALE_PROMEMORIA_CRON` (di base `0 0 9 * * *`, «-» per
spegnerlo). Senza `SPRING_MAIL_HOST` le email si registrano come «non partite».
