# 0029 · Eliminare ciò che lo storico usa senza tenerne una copia

**Stato:** accettata (applicata a serie delle ricevute e casse, blocco 7 lotto 2; le altre aree seguono nei lotti 3–5)

## Contesto

L'ADR 0028 mette il cestino su tutte le righe degli elenchi modificabili e permette di eliminare davvero ciò di cui lo
storico conserva una copia presa alla registrazione (il metodo di pagamento). Molte cose delle impostazioni una copia
non ce l'hanno: le serie delle ricevute (le ricevute emesse portano il numero della serie), le casse (sessioni,
pagamenti e uscite), le postazioni (turni), i servizi (utilizzi), i ruoli (assegnazioni). Per queste serve una regola
che dica quando il cestino elimina e quando si ferma.

## Decisione

- **Una cosa si elimina davvero in due casi:** non è mai stata usata, oppure lo storico ne conserva una copia
  (ADR 0028). Altrimenti niente cancellazione e niente stato «archiviato»: si spegne.
- **Se lo storico la usa senza copia**, al secondo tocco del cestino l'eliminazione si ferma (risposta 409) con un
  messaggio su due righe, nel formato del design system («Messaggi di blocco»): la prima dice cosa la usa, la seconda
  propone di spegnerla («Puoi spegnerla: …»). Chi legge sa prima di toccare se la riga è eliminabile: il DTO porta
  `eliminabile` e `motivoBlocco`, calcolati solo per chi ha il permesso di modificare.
- **Il controllo sta nel servizio**, non in un vincolo del database. Se l'uso sta in un altro modulo, il servizio
  passa da un'interfaccia pubblica del modulo che lo conosce (come `ControlloMetodi`, `Pagamenti.registratiPerCassa`):
  un modulo non legge le tabelle di un altro.
- **Non hanno il cestino** ciò che il sistema richiede (il borsellino, la cassa principale, l'ultima serie di ricevute
  accesa) e le cose che durano, che si chiudono con una data di fine (tariffe, appartenenze).
- **Una stagione con un listino** non si elimina finché il listino c'è (lotto 3). **Un ruolo** si elimina indicando un
  sostituto, in una sola transazione (lotto 5).
- **Ogni elenco riordinabile** ha il suo `PUT …/ordine` con `{"ids": [...]}`, in una transazione, e risponde con
  l'elenco aggiornato (un array).
- **Le eliminazioni rare che toccano tanti dati** usano la finestra di «eliminazione pesante» del design system, non
  il doppio tocco.

### Applicazione in questo lotto

- **Serie delle ricevute** (`RicevuteService`). Si elimina solo se non ha mai emesso una ricevuta (anche annullata: il
  numero resta usato). Se ne ha emesse, 409:
  - «Non si può eliminare: la serie «X» ha emesso 412 ricevute.» / «Puoi spegnerla: le ricevute restano con il loro
    numero, ma la serie non verrà più usata per le nuove.» (per una serie di fatture: «fatture»; se è già spenta, la
    seconda riga dice «È già spenta: le ricevute restano con il loro numero e la serie non viene più usata per le
    nuove.»).
  - L'ultima serie `RICEVUTA` accesa è «richiesta dal sistema»: non si elimina («Non si può eliminare: «X» è l'ultima
    serie di ricevute accesa.» / «Per eliminarla accendine prima un'altra: senza una serie accesa non si possono fare
    ricevute.») e non si spegne («Non si può spegnere: «X» è l'ultima serie di ricevute accesa.» / «Accendine prima
    un'altra: senza una serie accesa non si possono fare ricevute.»). Le serie di fatture non sono protette.
- **Casse** (`CassaService`). Si elimina solo se non ha mai aperto una sessione e non ha pagamenti o uscite registrati.
  Altrimenti 409:
  - «Non si può eliminare: la cassa «X» ha 58 sessioni nello storico.» / «Puoi spegnerla: le sessioni restano
    com'erano, ma la cassa non verrà più proposta per le nuove.» (l'elenco dice anche i pagamenti e le uscite
    registrati; se la cassa è aperta, la seconda riga è «Chiudila e poi puoi spegnerla: …»; se è già spenta, «È già
    spenta: …»).
  - La cassa principale non si elimina («Non si può eliminare: «X» è la cassa principale.» / «Per eliminarla scegli
    prima un'altra cassa come principale.») e, nell'interfaccia, non ha né cestino né interruttore: per spostare il
    ruolo si sceglie un'altra cassa come principale.

## Conseguenze

- Eliminare serie, casse o offerte già usate resta escluso. Per ammetterlo servirebbe una copia nello storico (come
  per i metodi di pagamento) e un nuovo ADR, dopo che `ricerca-dominio` avrà confrontato le pratiche del settore
  (numerazione delle ricevute, storico di cassa).
- Chi ha un passato si spegne: lo storico resta com'era e la cosa non viene più proposta per il nuovo.
- Ogni area nuova che adotta la regola scrive il suo messaggio di blocco nel servizio e lo mostra con `righeBlocco`.
