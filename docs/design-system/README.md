Il gestionale di Leone XIII Sport (e di qualunque società sportiva lo usi) parla lo stesso linguaggio visivo di ClockWork: si chiama **Corsia**, come la corsia di una vasca. Carta calda per lo sfondo, un inchiostro blu notte per testo e superfici forti, un solo verde cloro per le azioni, i colori delle aree e delle categorie solo per riconoscere le cose. A differenza di ClockWork non vive solo sul telefono: la segreteria lo usa tutto il giorno su un PC alla cassa, lo staff dal telefono tra un turno e l'altro. Una schermata deve sembrare un registro tenuto bene: si trova la persona, si legge lo stato, si fa l'azione, senza cercare dove cliccare.

## Contenuti

- **Lingua e voce.** Italiano, del «tu», frasi brevi. L'app spiega cosa succede e cosa resta com'era: «Eliminando una stagione i tesseramenti già registrati restano com'erano», «Il pagamento resta nell'elenco "Da incassare" finché qualcuno non lo segna arrivato sul conto».
- **Maiuscole solo a inizio frase**, anche su pulsanti, titoli e voci della barra: «Nuovo abbonamento o carnet…», «Metodi di pagamento». Le etichette `.lbl` sono l'unico testo in maiuscolo, ottenuto con CSS.
- **I pulsanti a vista sono verbi con l'oggetto**: «Registra il tesseramento 2026/27 · 35 €», «Aggiungi un prezzo», «Scarica il foglio ore (.xlsx)». Mai «OK» o «Invia». I puntini «…» dicono che si apre un modulo prima di fare qualcosa («Nuovo cliente…»). Mentre lavorano diventano un gerundio coi puntini: «Salvo…».
- **Le azioni di riga non hanno testo a vista** (vedi «Azioni» qui sotto): il testo c'è lo stesso, ed è più preciso di quello che starebbe in un link, perché nomina la cosa: «Modifica la lezione Mar 17:00–17:45», «Togli Giulia Rossi dalla lista d'attesa».
- **Le conferme sono domande corte**: «Elimina?», «Revoca?», «Togli?», «Disattiva?». Le azioni che si annullano da sole (spegnere un metodo, sospendere) non chiedono conferma.
- **Gli errori dicono cosa fare**: cosa non va, poi l'alternativa. Gli avvisi ai clienti (tornello, macchine) parlano al cliente, non all'operatore.
- **Numeri all'italiana.** Euro come li scrive `toLocaleString('it-IT')`: «416,00 €», «35 €» quando è tondo. Orari col trattino lungo senza spazi: «17:00–17:45». Date brevi «24/10/2026» negli elenchi, per esteso «Sabato 24 ottobre» nei titoli. Tutte le cifre in colonna usano `mono`.
- **Punteggiatura di casa:** «·» per separare («Carnet 100 ingressi · entrata libera · 100 rimasti»), virgolette basse «» per nomi di cose dell'app («Borsellino», «Da incassare»), «→» per dove porta un link.
- **Dati di minori.** Negli esempi, negli screenshot e nella demo solo persone inventate. Mai un nome vero in un'anteprima.
- Niente emoji.

## Fondamenta visive

**Colore.** Neutro prima di tutto. La pagina è `carta`; card, input e barra laterale sono `surface`, separati da un bordo `line`. Il testo è `ink`, il secondario `muted`. `cloro` è l'unico colore di marca: voce attiva della barra, link, azioni che aggiungono o confermano, anello di focus. Ha una tinta, `cloro-soft`, per conferme, suggerimenti, voce attiva e pulsanti-icona premuti, sempre con testo `cloro-ink`. Gli **avvisi sono ambra** (`warn` su `warn-bg`): manca qualcosa, da pagare, da incassare, quota mancante. Il **rosso** `danger` resta per le azioni distruttive (eliminare, togliere, revocare, disattivare). `fill` (con `on-fill`) è il pulsante primario: in chiaro è l'inchiostro, nel tema scuro si inverte. Non usare mai `ink` come fondo: nel tema scuro è un colore chiaro.

**Colori di significato.** Tre famiglie con la loro terna (pieno · fondo · testo) e un solo uso ciascuna. Le **aree** dello staff (`area-m` blu maschile, `area-f` magenta femminile, `area-p` arancio piccoli, `area-a` viola atrio, `area-x` grigio altro, `area-v` verde) sui tag delle postazioni. Le **categorie del piano vasca** riusano le stesse terne con le classi `.cat-*` (scuola nuoto blu, acquaticità arancio, agonismo magenta, master e privata viola, acquagym cloro, prova verde, gara ambra, il resto grigio) e si disegnano con `--c-bg`, `--c-fg`, `--c-line`. L'**esito del tornello** ha `passa` (verde), `ferma` (rosso) e `oro` (golden pass, sorveglianza). Un colore di significato non si usa mai per il testo libero o per l'interfaccia, e non è mai l'unico segnale: il tag dice l'area per esteso, la lezione dice il corso, l'esito ha la spunta o la croce e la frase.

**Tipografia.** Tre famiglie servite dall'app stessa (pacchetti @fontsource, solo latino, funzionano anche senza rete nel programma per Windows). `display` (Bricolage Grotesque 800) per titoli di pagina, nomi nelle schede, data del piano vasca, cifre grandi: «Leonardo Ferri», «Sabato 24 ottobre». `body` (IBM Plex Sans 400–700) per tutto il resto. `mono` (IBM Plex Mono 500) per orari, euro, codici dei braccialetti e date in colonna, con `tabular-nums`. Testo di base 15px/1,4; i campi `.inp` 16px, mai meno (Safari ingrandisce). Ogni taglia viene da una delle 11 variabili `--text-*` (11 · 12 · 13 · 14 · 15 · 16 · 17 · 20 · 28 · 32 · 40px).

**Spaziatura.** Ogni gap, padding e margin usa una variabile `--space-*` (2 · 4 · 6 · 8 · 10 · 12 · 14 · 16 · 20px). Dentro le card: padding 14–16px, gap 10px. Pulsanti affiancati: gap 8px. Pulsanti-icona affiancati: gap 4px, così le aree di tocco da 44px si toccano senza sovrapporsi.

**Forma.** Card `radius` (16px). Pulsanti grandi, pulsanti-icona con cornice e binario segmentato `radius-lg` (14px). Input, messaggi, pulsanti piccoli e pulsanti-icona `radius-md` (12px). Segmenti e riquadri icona `radius-sm` (10px). Tag, chip e interruttori sono pillole.

**Bordi, non ombre.** Le superfici si separano con 1px `line`; i divisori interni delle righe sono `line-soft`. L'unica ombra è `shadow-seg`, sotto il segmento selezionato.

**Stati.** Selezionato = pieno `fill` (chip) oppure `surface` con ombra sul binario `surface-sunken` (segmenti). Voce attiva della barra = fondo `cloro-soft`, icona `cloro`. Pulsante-icona premuto (interruttore) = fondo `cloro-soft`. Disabilitato = opacità 0,5 (pulsanti) o 0,4 (pulsanti-icona). Spento o annullato = riga a opacità 0,6 più un tag che lo dice. Errore = fondo `warn-bg` più il triangolo d'avviso. Armato (in attesa di conferma) = la domanda a parole dentro un bordo nel colore dell'azione. Focus da tastiera = anello 2px `cloro` a 2px dal bordo su tutto ciò che si tocca; 1px per gli input. Nessuno stato si affida solo al colore.

**Tocco.** Ogni controllo si tocca su almeno 44px. Pulsanti grandi 48px; pulsanti-icona 40px a vista con un'area invisibile da 44px; pulsanti piccoli e chip 36px con l'area da 44px centrata. Le righe con un interruttore sono un `<label>`.

**Layout.** Due forme, decise da `schermo.larga` (da 1024px). Sul PC una barra laterale fissa con le sezioni a gruppi (Il mio lavoro · Piscina · Accessi · Gestione) e in fondo le impostazioni, che scorrono in un secondo pannello; le pagine «larghe» (piano vasca, clienti, incassi) usano tutto lo spazio, le altre una colonna leggibile (760–860px). Sul telefono la barra va in basso con le voci principali e «Altro». I moduli si aprono in un **foglio**: dal basso sul telefono, pannello a destra sul PC, con il pulsante-icona «Chiudi» in alto.

**Schede lunghe: riassunto e schede.** Una pagina che raccoglie tante cose di una persona (la scheda del cliente) non è una griglia di card da scorrere. In alto un **riassunto** sempre visibile: chi è, l'esito al tornello, quattro valori (da pagare, credito, crediti di phon e docce, braccialetto; ambra quando c'è qualcosa da sistemare) e le azioni di tutti i giorni come pulsanti grandi; le azioni rare in un menu «Altre azioni» (pulsante-icona `altro` con cornice). Sotto, le sezioni sono **schede** (`role="tab"`, frecce e Inizio/Fine per spostarsi): il nome della scheda porta un segno quando c'è qualcosa (`1`, o ambra «1 da fare», «da pagare»). La scheda aperta resta la stessa passando da un cliente all'altro. Le sezioni vuote sono una riga con il «+» nell'intestazione della card, non un pulsante grande.

**Temi.** Chiaro e scuro, segue il sistema: lo script in `index.html` mette `data-tema="chiaro|scuro"` su `<html>` prima del primo disegno. Il tema scuro è un solo blocco `:root[data-tema='scuro']` che ridefinisce i colori. Ogni nuovo colore va dato in entrambi i temi.

**Contrasto.** Tutto il testo supera il 4,5:1 sugli sfondi dove appare, in entrambi i temi; bordi dei controlli, icone dei pulsanti-icona e anello di focus superano il 3:1. Lo controlla un test (vedi sotto).

## Azioni

Il testo che fa anche da pulsante (i vecchi link «Modifica», «Annulla», «Togli», «Spegni» accanto a ogni riga) non c'è più. Le azioni seguono quattro forme, in quest'ordine di peso:

1. **Pulsante grande** (`.btn`, 48px): l'azione della schermata o del modulo. `btn-primary` una per vista («Registra», «Salva le regole»), `btn-secondary` l'alternativa accanto («Annulla», «Nuovo cliente…»), `btn-danger` solo per eliminare con conferma nel pulsante stesso. Ha un'icona a sinistra quando aiuta a riconoscerlo: `piu` per «Nuovo…», `scarica` per i file, `matita` per «Modifica…».
2. **Pulsante piccolo** (`.btn .btn-sm`, 36px): azioni secondarie dentro una card, sempre con l'icona: «Aggiungi un prezzo», «Aggiungi una postazione», «Modifica l'anagrafica», «Scarica il foglio ore (.xlsx)». La variante `btn-quieto` (senza bordo, testo `cloro`) è per «Mostra tutto / Solo gli ultimi», con la freccia `giu` o `su` e `aria-expanded`.
3. **Pulsante-icona** (`Azione.svelte`, 40px): le azioni di una riga e delle intestazioni. Senza cornice dentro gli elenchi, con cornice (`cornice`, 44px) quando sta da solo in un'intestazione (frecce del calendario, «Torna a oggi», «Chiudi» del foglio). Grigio `muted` a riposo, `cloro` se aggiunge o conferma, `danger` se toglie o revoca. Si mettono in un `.gruppo-azioni` in fondo alla riga, nell'ordine: sposta · modifica · cambia stato · togli.
4. **Link nel testo** (`a`, `cloro`): solo dentro una frase o per un nome che apre la sua scheda.

**Il pulsante-icona porta sempre un'etichetta** (`etichetta`): diventa il nome per lo screen reader e il suggerimento al passaggio del mouse. **Conferma in due tocchi** (`conferma="Elimina?"`): il primo tocco arma il pulsante, che mostra la domanda; il secondo esegue; si disarma dopo 5 secondi o uscendo dal pulsante. **Interruttori** (`premuto`): per le cose che si accendono e si spengono restando lì (il QR del telefono, «Da incassare a mano», il testo di un promemoria), con `aria-pressed`.

## Attività in calendario

Un corso, un turno o un evento si mostra sempre come un **riquadro nella tinta della sua categoria** (`.cat-*`: sfondo `--c-bg`, testo secondario `--c-fg`), senza bordo laterale: il titolo resta in `ink`, il pallino del livello (se il corso ne ha uno) sta davanti al titolo, corsie e iscritti in `mono` piccolo. Pieno → numeri in `warn`; sovrapposto → bordo interno `warn` di 2px; sospeso → mezzo trasparente e barrato. Lo stesso riquadro vale nella pianta delle vasche, nell'orario per corsia, nella settimana e nell'agenda del telefono.

Lo spazio libero è un riquadro **tratteggiato** (`--line-dashed`): se chi guarda può pianificare, è un pulsante che al passaggio si colora di `cloro-soft` e mostra il `piu`; il clic apre un piccolo menu («Turno di un corso…», «Evento…») che porta al modulo già compilato con giorno, orario e corsia.

**Menu rapido.** Un piccolo pannello sopra la griglia per creare qualcosa al volo da una casella libera (un turno nella scheda del corso): intestazione su fondo `hero` con «Nuovo turno» e il quando, poi le scelte una sotto l'altra con l'etichetta sopra e una linea `line-soft` tra l'una e l'altra (pillole per le scelte, corsie occupate tratteggiate e spente), in fondo un piede su `carta` con il riepilogo di cosa verrà creato e il pulsante primario che lo dice («Aggiungi 2 turni»). Il titolo segue le scelte; tutto il resto si cambia dopo, dalla pagina della cosa creata, che si apre subito.

**Viste a scelta.** Quando la stessa informazione ha due letture utili (il piano vasca: una fascia alla volta o tutto il giorno; la settimana in riquadri o per corsia) si offre un selettore segmentato nella barra, e la scelta si ricorda per ogni account sul dispositivo (`lib/preferenze.ts`). Il predefinito dipende dal ruolo quando i ruoli lavorano in modo diverso: in segreteria i riquadri, per chi coordina la vista per corsia.

**Banco del tornello.** L'esito dell'ultimo letto sta a sinistra, grande, con sotto il riepilogo del cliente e «Da sistemare»; a destra tre numeri della giornata e i passaggi di oggi. L'esito dà il colore a tutto: verde `passa` per chi entra, rosso `ferma` per chi è fermato, `line-strong` per chi esce, `oro` per il golden pass (striscia in alto nelle tessere, nodo nella linea del tempo, barra a sinistra nella tabella). Il corso di oggi di chi passa è una pillola nella tinta della categoria con il pallino del livello. Chi è fuori ha sempre il rimedio a portata: l'azione è un link o un bottone chiaro bordato di `ferma`, mai un bottone scuro pieno accanto alle pastiglie rosse. «Passaggi di oggi» (tessere, tabella con i filtri, per corso, linea del tempo) e «Da sistemare» (schede, righe, pillole, per motivo) sono viste a scelta, dalla rotella della barra del lettore; ognuna ha sotto lo storico «Sistemati oggi», più tenue (spunta `passa`, testo `muted`).

**Scadenze e incassi.** Le scadenze si leggono per urgenza: scaduti o mancanti in `ferma`, oggi e la settimana in `warn`, il resto neutro; il tipo ha la sua icona in un quadretto tinto (certificato `area-m`, abbonamento `cloro`, carnet `area-p`, tesseramento `area-a`), uguale nell'elenco e nel calendario. Scegliere più righe fa comparire una barra `hero` con l'azione su tutte. Gli incassi danno a ogni metodo un colore fisso (nell'ordine dei metodi della società) per la barra del totale, il grafico e le etichette; il totale del giorno sta su fondo `hero`. Una funzione non ancora pronta resta visibile con l'etichetta «in sviluppo» su `oro-bg` e, al clic, dice in un `hint` che cosa manca, senza salvare niente.

## Iconografia

Settantasette icone disegnate a mano, nella stessa famiglia di ClockWork, in `src/lib/icone.ts`: griglia 24×24, tratto 1,8 con estremi e angoli arrotondati, nessun riempimento, `currentColor`. Si mostrano a 22px nei pulsanti grandi e nella barra laterale (18px), 20px nei pulsanti-icona, 18px nei pulsanti piccoli. Sono sempre `aria-hidden`: il nome sta nel controllo. I nomi sono in italiano e dicono il significato, non la forma, e sono un tipo TypeScript (`NomeIcona`): un nome sbagliato non compila.

- **Azioni:** `piu`, `meno`, `matita`, `chiudi`, `check`, `cestino`, `annulla` (annulla una registrazione, lo storico resta), `revoca`, `divieto` (banna), `spegni`, `pausa` (sospendi), `termina`, `su`, `giu`, `sx`, `dx`, `indietro`, `freccia`, `scambio`, `aggiorna`, `scarica`, `copia`, `salva`, `occhio`, `occhio-no`, `altro` (il menu «Altre azioni»), `cerca`, `campana` (avvisa), `testo`, `firma` (registra un consenso), `chiave`, `esci`, `server`, `qr`, `scollega`, `storico`, `oggi`, `info`, `avviso`, `telefono`, `nfc`.
- **Persone e cose:** `persona`, `persona-piu` (iscrivi), `persona-meno` (ritira, togli un ruolo), `persone`, `euro`, `golden` (golden pass), `incassato`, `braccialetto`, `smarrito`, `ritrovato`, `riconsegna`.
- **Sezioni:** `turni`, `richieste`, `ore`, `pianifica`, `approva`, `modelli`, `postazioni`, `piano`, `corsi`, `tornello`, `clienti`, `scadenze`, `promemoria`, `societa`, `listino`, `incassi`, `metodi`, `macchine`, `borsellino`, `attesa`, `account`, `ruoli`, `regole`, `impostazioni`, `tariffe`.

Per un'icona nuova si disegna sulla stessa griglia con lo stesso tratto e si aggiunge a `icone.ts`; niente librerie di icone, niente emoji, niente frecce di testo (‹ › ↑ ↓ ×).

## Logo

Non c'è ancora un marchio: il nome «Leone» si scrive in `display` 800, `cloro`, con sotto il nome della società in `.lbl`. Il programma per Windows usa per ora l'icona di Tauri.

## Dove vive

- **Il codice è la verità.** Le variabili e le regole globali (`.btn`, `.azione`, `.card`, `.tag`…) sono in `frontend/src/app.css` di [Doc-98/leone-gestionale](https://github.com/Doc-98/leone-gestionale); i componenti in `frontend/src/components` (`Icona.svelte`, `Azione.svelte`, `Foglio.svelte`). Se questo documento e l'app non coincidono, vince l'app.
- **I contrasti sono un test.** `frontend/src/lib/contrasto.test.ts` legge `app.css` e controlla le coppie testo/sfondo (≥ 4,5:1) e segno/sfondo (≥ 3:1) in tutti e due i temi; se una scende, la CI si ferma. Ogni nuova coppia va aggiunta lì.
- **Lo stesso sistema, due copie:** questo file (`docs/design-system/README.md`) e la pagina «Leone Gestionale Design System» tra gli artifact, con le anteprime dei componenti.
