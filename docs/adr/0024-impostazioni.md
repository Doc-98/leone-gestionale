# 0024 · Impostazioni: panoramica, ricerca condivisa, aree riordinate, salvataggio unico

**Stato:** accettata

## Contesto

Le impostazioni erano cresciute una pagina alla volta (società, listino con le regole di prezzo, borsellino, servizi,
tariffe, account, ruoli…) e non si trovava più dove stesse una cosa. Dal mockup «Impostazioni» (novembre 2026) è uscito
un riordino con poche regole che valgono per tutte le pagine. Il gestionale resta generico: ogni scelta che a Leone XIII
serve in un modo (2 rate, rimborso nel borsellino, credito di sospensione su tutte le settimane) è un'impostazione.

## Decisione

- **Aree e pagine.** Panoramica; *La società* (Dati, stagioni e gruppi; Calendario della stagione); *Prezzi e pagamenti*
  (Listino; Regole di prezzo; Metodi di pagamento); *Clienti e accessi* (Regole del tornello; Promemoria email; Phon e
  docce); *Staff* (Mansioni, postazioni e tariffe); *Sistema* (Account e ruoli). Pagine unite: borsellino + servizi e
  macchine → «Phon e docce»; mansioni + tariffe → «Mansioni, postazioni e tariffe»; account + ruoli → «Account e ruoli»
  (due schede). Le regole di prezzo hanno una pagina loro, fuori dal listino. Gli indirizzi vecchi (`#/borsellino`,
  `#/tariffe`, `#/ruoli`) portano alla pagina nuova. «Lista d'attesa» sta nei Promemoria, «una famiglia paga per più
  persone» nei Metodi di pagamento (i dati restano nella società); un avviso viola, che si chiude e non ricompare per
  quella persona, spiega le novità dopo l'aggiornamento.
- **Panoramica.** In cima «Da sistemare» (dati per le ricevute mancanti, posta non configurata, stagione futura senza
  listino, macchine mai collegate, mansioni senza tariffa), poi un riquadro per area con i valori che contano. Ogni dato
  si legge solo con il permesso per vederlo. Sul telefono è l'elenco delle impostazioni con la ricerca e le cose da sistemare.
- **Ricerca condivisa.** In cima a ogni pagina delle impostazioni (sul PC ferma mentre si scorre, Ctrl K / ⌘K) una
  ricerca su un indice di voci (`lib/impostazioni.ts`): ogni parola cercata deve essere l'inizio di una parola della voce,
  senza badare ad accenti e maiuscole; si vedono solo le voci delle pagine visibili. Il risultato porta alla pagina e,
  con `#/pagina/riquadro`, scorre al riquadro (`id="imp-…"`).
- **Forma delle pagine.** Ogni sezione è una scheda con l'intestazione in una fascia color cloro (`.sez`, `.sez-t`): le
  intestazioni non si devono confondere con il fondo della pagina. Le righe d'impostazione hanno etichetta e spiegazione a
  sinistra, il controllo a destra, sotto se non c'è spazio (`.riga-imp`): testo e controlli non si sovrappongono mai.
- **Salvataggio unico.** Le modifiche di una pagina si salvano insieme da una barra in basso («N modifiche da salvare»,
  Annulla, Salva) che compare solo se c'è qualcosa da salvare. Le azioni che non si possono «tenere in sospeso»
  (creare una macchina e vederne la chiave, eliminare, dare un ruolo) restano immediate.
- **Regole di prezzo.** La stagione su una linea, con le corsie di riduzione e recesso, la linea di oggi (si tocca per
  tornarci) e la scadenza delle rate in rosso (l'etichetta compare passandoci sopra). Toccando o trascinando la linea si
  sceglie un giorno qualsiasi e un riquadro mostra cosa succede a chi si iscrive quel giorno, per l'abbonamento scelto:
  quanto paga, le rate scritte «n × quota» (dopo l'ultima scadenza «1 × totale»: non si mostrano rate che non si possono
  più scegliere), il rimborso in caso di recesso. Il calcolo dell'esempio rifà quello del server (`lib/impostazioni.ts`).
  La sospensione ha due parametri nuovi: `settimaneRestituite` (`TUTTE` o `OLTRE_SOGLIA`) e `restituzione`
  (`BORSELLINO`, `ALLUNGA`, `A_SCELTA`); il recesso ha `rimborso` (`BORSELLINO`, `DENARO`, `A_SCELTA`) al posto di
  `nelBorsellino`, che nelle regole già salvate si legge ancora.
- **Le regole al banco.** Sospendendo un abbonamento del listino (con una fine) si applica la regola di sospensione
  della stagione: si conta a giorni (una settimana = 7 giorni); se lo stop non supera la soglia, o il certificato
  medico non è arrivato in tempo (lo spunta chi sospende), non si restituisce niente e l'abbonamento non vale in quei
  giorni. Altrimenti si restituiscono tutti i giorni dello stop o solo quelli oltre la soglia: allungando
  l'abbonamento, oppure come credito nel borsellino pari al prezzo dell'abbonamento diviso per i suoi giorni, per i
  giorni restituiti; con `A_SCELTA` chi sospende scrive cosa ha scelto il cliente. Ogni sospensione ricorda i giorni di
  cui allunga l'abbonamento e il credito dato (V23); annullandola il credito si toglie. Senza la regola, o per gli
  abbonamenti fuori listino, la scadenza si allunga di tutti i giorni sospesi, come prima. Allo stesso modo, nel
  recesso con `A_SCELTA` chi termina l'abbonamento scrive se il cliente vuole il rimborso nel borsellino o in denaro.
- **Ruoli in lista.** I ruoli sono un elenco (predefiniti e creati dalla società) con i permessi del ruolo scelto a
  destra: la pagina regge anche 20 o 40 ruoli. Il confronto di 2–3 ruoli affiancati, con le differenze evidenziate, si
  apre a richiesta. Eliminando un ruolo assegnato si può dare un altro ruolo, nello stesso impianto, a chi lo aveva; i
  ruoli predefiniti non si eliminano. Dove vale un ruolo si dice «in tutti gli impianti» o «solo nell'impianto X».
- **Ore da coprire.** Ogni postazione può dire quante ore a settimana va coperta (V24, facoltativo). Nelle impostazioni
  la mansione mostra le ore da coprire e il costo stimato con la tariffa di base; in pianificazione ogni riga mostra
  «ore pianificate / ore da coprire», in arancio se sono meno, e le postazioni sotto le ore vanno in «Da sistemare».
- **Promemoria.** Ogni email è una pillola sulla linea dei giorni intorno alla scadenza; l'editor del testo inserisce i
  dati del cliente (le etichette `{nome}`, `{scadenza}`…) dove sta il cursore, con l'anteprima accanto.

## Conseguenze

- Una sola forma per tutte le pagine delle impostazioni: chi ne impara una le sa usare tutte.
- L'indice della ricerca va tenuto aggiornato quando si aggiunge un'impostazione (la voce e, se serve, il riquadro).
- Una regola di sospensione accesa vale per ogni sospensione degli abbonamenti del listino, anche quelle che non sono
  per malattia: chi sospende vede prima cosa si restituirà.
