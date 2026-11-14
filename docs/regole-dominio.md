# Regole di dominio e principi

Il riferimento comune per chiunque lavori sul gestionale: sessioni principali e subagenti (`.claude/agents/`).
Le decisioni nel dettaglio stanno negli ADR (`docs/adr/`); qui ci sono le regole che valgono ovunque e che è facile
rompere senza accorgersene. Se una regola cambia, si aggiorna qui e nell'ADR che la riguarda.

## Principi di prodotto

- **Per qualsiasi società sportiva.** Il gestionale non è su misura per la Leone XIII Sport: prezzi, sconti, stagioni,
  quote, regole e testi sono impostazioni, non valori nel codice. I dati della Leone servono solo come dati di prova.
- **Davanti a un bivio, tutte le strade.** Se le società possono lavorare in modi diversi, si implementano tutte le
  opzioni e un'impostazione sceglie quale usare, invece di costruire solo il modo della Leone.
- **Libertà per chi lo usa.** Ogni automatismo che limita (blocchi, regole automatiche, solleciti, promemoria) si
  accende e si spegne, con tempi impostabili, e/o è un permesso da dare o no a un ruolo. Il programma avvisa, la
  persona decide.
- **Lo sport è una dimensione, non un modulo.** Corsi, spazi e mansioni appartengono a un impianto e a una
  disciplina: un altro sport si aggiunge senza riscrivere il sistema.
- **Niente si cancella.** Le cose si chiudono con una data di fine e lo storico resta.

## Privacy e dati

- Ognuno vede solo ciò che gli serve; i permessi si controllano nel backend, non solo nascondendo i pulsanti.
- Ogni consultazione della scheda di un cliente si registra (ADR 0003, 0014).
- **Nessun dato reale** nel repository, nei test, negli screenshot, nei mockup e nella demo, soprattutto di minori:
  solo persone inventate (`db/demo`, profilo `demo`). I dati veri restano nel profilo `locale`, fuori dal repository.
- Un account famiglia non ha nessun permesso del gestionale; un account dello staff non usa le API delle famiglie.
  Un genitore che lavora anche nella società ha due account distinti (ADR 0027).

## Turni e staff

- **Spogliatoi per sesso.** Lo staff maschile non va mai nello spogliatoio femminile e quello femminile mai nel
  maschile: vale ovunque si assegnano turni o postazioni (pianificazione, settimana tipo, generazione del mese,
  sostituzioni, scambi).
- **Una persona per postazione.** Una postazione tiene una persona alla volta (al massimo un turno al giorno); se
  serve un'altra persona si crea un'altra postazione.
- **Accavallamenti: solo un avviso.** Una persona può avere più turni nello stesso giorno; se si accavallano il
  programma avvisa e chi coordina può confermare lo stesso. Negli elenchi di scelta chi ha un turno accavallato è
  nascosto di partenza, con l'opzione per mostrarlo.
- **Niente si «respinge».** Gli assistenti degli spogliatoi sono collaboratori (co.co.co.): la società non può imporre
  orari. Sostituzioni, cambi d'orario e scambi non si respingono: chi coordina approva, conferma o lascia il turno
  scoperto.
- **Settimane tipo.** Il programma parte con una sola settimana tipo; chi pianifica ne crea, modifica, rinomina ed
  elimina quante vuole.

## Soldi

- Pagamenti, chiusura di cassa, ricevute e simili sono **provvisori** finché non si sa come lavora davvero la
  segreteria: prima di cambiarli si confrontano con le pratiche del settore e con altri gestionali (subagente
  `ricerca-dominio`), e le scelte vanno in un ADR.
- Pagamenti online: solo bonifico con causale generata dall'app e addebito diretto SEPA, senza spingere. Niente carte
  senza una richiesta esplicita.

## Macchine e braccialetti

- Phon, docce e tornello: si assumono macchine non in rete che leggono e scrivono il braccialetto (ADR 0018, 0025),
  finché non si sa di più su come comunicano.

## Tecnica

- Codice di dominio, commenti, messaggi e documentazione in italiano; i termini tecnici standard restano in inglese.
- Backend: monolite modulare; un modulo non legge le tabelle di un altro, usa le sue classi pubbliche o gli id.
- Lo schema cambia solo con **nuove** migrazioni Flyway (`V<n>__descrizione.sql`): mai modificare quelle pubblicate.
- Orari «da calendario» come data + ora locale, fuso Europe/Rome.
- Gestionale e app delle famiglie nello stesso repository: le schermate delle famiglie in `frontend/src/famiglie/`
  non importano quelle del gestionale; in comune solo stili e `src/lib` (ADR 0027).

## Interfaccia

Il sistema visivo «Corsia» è in `docs/design-system/README.md`. In più, regole fisse:

- Le etichette non vanno a capo («Postazione 1» resta su una riga): se lo spazio non basta si rimpicciolisce il
  carattere o si accorcia il testo.
- Le fasce d'intestazione e i titoli di sezione hanno abbastanza contrasto con lo sfondo della pagina e con le card,
  in entrambi i temi.
- Niente blocchi di testo esplicativo nella pagina: le spiegazioni stanno in un fumetto al passaggio su una piccola
  icona «i».
- Le azioni sono sempre pulsanti veri, mai testo cliccabile (i link solo dentro una frase o per un nome che apre la
  sua scheda).
- Il selettore segmentato solo fino a 3 opzioni; con più opzioni un altro controllo.
- Tre misure da controllare sempre: computer (sopra i 1024 px), tablet (834×1194 e ruotato) e telefono (390×844),
  come nella vetrina della demo (ADR 0027).

## Comunicazione con la società

- Il gestionale è una **demo** con dati finti. Nel materiale per persone non tecniche (la società, la direzione) non si
  scrive «fatto» accanto alle funzioni: si usano stati come «Prova nella demo», «In sviluppo», «Da fare».
