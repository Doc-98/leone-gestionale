# 0023 · Gestione dei turni: accavallamenti con avviso, una persona per postazione, postazioni per sesso, calendario della stagione

**Stato:** accettata

## Contesto

Dal mockup «Gestione turni» (ottobre–novembre 2026) sono uscite la pianificazione in tre viste (per postazione, per
persona, pianta), la settimana tipo come insieme di scelte rapide, la pagina «Richieste» del coordinatore e il calendario
della stagione. Tre regole sono emerse lavorando sui mockup:

- **una postazione è una persona alla volta**: se serve qualcuno in più si aggiunge una postazione;
- **gli spogliatoi sono riservati**: lo staff maschile non lavora nello spogliatoio femminile e viceversa;
- il programma deve solo impedire le contraddizioni (fare due cose nello stesso posto), non decidere al posto del
  coordinatore: **due turni accavallati della stessa persona** (es. un passaggio di consegne) si segnano, con un avviso.

Il gestionale resta generico: la riserva per sesso è un'impostazione della postazione, non una regola sui nomi.

## Decisione

- **Accavallamenti (V22)**: via il vincolo `turno_senza_sovrapposizioni`. Creare o modificare un turno che si accavalla
  con un altro della stessa persona risponde **409 con `avviso: true`** (eccezione `condiviso.Avviso`); l'interfaccia
  mostra il messaggio e, se l'utente conferma («Assegna comunque»), ripete con `forza: true`. Lo stesso per proporre una
  sostituzione a chi è già in turno (`POST /api/richieste/{id}/proponi` con `forza`). Chi accetta da sé una
  sostituzione aperta o propone uno scambio continua a non potersi accavallare.
- **Una persona per postazione**: un turno pianificato non può accavallarsi con un altro sulla stessa postazione (anche
  scoperto): 409 «una postazione è per una persona alla volta». Vale anche per le righe della settimana tipo. Il controllo
  è nel servizio (`TurnoRepository.sullaPostazione`), non nel database, per non fallire la migrazione su dati vecchi.
- **Postazioni per sesso**: `postazione.solo_sesso` (`M`, `F` o null). Chi non ha quel sesso in anagrafica non può
  avere il turno (400), non compare tra i candidati di una sostituzione e non entra negli scambi; nella generazione del
  mese il turno nasce scoperto con un avviso. `identita.Persone.sessi` dà il sesso agli altri moduli.
- **Calendario della stagione**: i giorni di chiusura e a orario ridotto sono **chiusure di tutto l'impianto del piano
  vasca** (eventi `CHIUSURA` senza spazi, già usati per sospendere le lezioni): tutto il giorno = 00:00–23:59, «chiude
  alle 13» = 13:00–23:59, «apre alle 10» = 00:00–10:00. `GET /api/chiusure` le elenca per la pagina (Impostazioni › La
  società), `pianovasca.Chiusure` le dà agli altri moduli. **Generando un mese** i turni nei giorni chiusi non si
  creano e quelli a cavallo di un orario ridotto si accorciano (`EsitoGenerazione.chiusi`).
- **Cambio orario**: come la sostituzione **non si respinge** (409). Si approva con «Risolvi» (`copriBuco: true`:
  l'orario lasciato libero diventa un turno scoperto sulla stessa postazione) o «Lascia scoperto». Gli scambi si
  approvano o si respingono come prima.
- **Settimane tipo**: se ne possono avere quante servono (crea da copia o vuota, rinomina, duplica, elimina); la
  validità resta nel modello ma l'interfaccia non la usa più (le nuove valgono dal 2000). Il «Genera un mese» mostra
  l'anteprima sul calendario: turni da creare, giorni chiusi o a orario ridotto, giorni già generati.
- **Interfaccia**: la pianificazione ha tre viste (`pianifica.vista`, ricordata sul dispositivo): per postazione (griglia
  per area e pannello del turno con «Assegna» a un tocco), per persona («Assegna qui», «+» per assegnare comunque),
  pianta (un giorno alla volta, trascina i nomi sulle postazioni). La pagina del coordinatore si chiama «Richieste»,
  quella personale «Le mie richieste».

## Conseguenze

- I dati vecchi con turni accavallati restano validi; due turni sulla stessa postazione nello stesso orario, se ci sono,
  vanno sistemati a mano (il controllo scatta alla prossima modifica).
- Modificando una postazione bisogna mandare `soloSesso` (null = chiunque), altrimenti la riserva si toglie.
- Un'area della griglia è il nome della postazione prima di « · » («Spogliatoio M · postazione 2» → «Spogliatoio M»),
  altrimenti la mansione; il colore viene dal nome come nelle altre pagine dello staff.
- Le chiusure del calendario valgono per tutto l'impianto: un evento di chiusura di un solo spazio resta del piano vasca
  e non tocca i turni.
