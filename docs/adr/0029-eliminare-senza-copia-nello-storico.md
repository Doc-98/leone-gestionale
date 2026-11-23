# 0029 · Eliminare ciò che lo storico usa senza tenerne una copia

**Stato:** accettata, sostituita in parte da 0030 (la regola «si elimina solo ciò che non è mai stato usato» è sostituita dall'ADR 0030; resta in vigore per serie delle ricevute, offerte vendute e stagioni usate, e finché le casse non sono riviste). Applicata a serie delle ricevute e casse (blocco 7 lotto 2) e a gruppi, stagioni e offerte (lotto 3)

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
- **Una stagione usata** non si elimina: la ferma ciò che la usa (offerte nel listino, vendite, tesseramenti, esiti dei
  livelli), con le chiusure del calendario che restano fuori (lotto 3). **Un ruolo** si elimina indicando un sostituto, in
  una sola transazione (lotto 5).
- **Ogni elenco riordinabile** ha il suo `PUT …/ordine` con `{"ids": [...]}`, in una transazione, e risponde con
  l'elenco aggiornato (un array).
- **Le eliminazioni rare che toccano tanti dati** usano la finestra di «eliminazione pesante» del design system, non
  il doppio tocco.

### Applicazione nel lotto 2

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

### Applicazione nel lotto 3

- **Gruppi di clienti** (`GruppiService`). Un gruppo non è mai bloccato: lo storico ne tiene la copia del nome, come per i
  metodi di pagamento (ADR 0028). La migrazione V35 aggiunge `appartenenza_gruppo.gruppo_nome`, rende `gruppo_id` nullable e
  vieta un'appartenenza senza gruppo che non sia chiusa e col nome copiato (`check`). La V35 aggiunge anche `gruppo_clienti.ordine`.
  - **Senza conseguenze** (nessuna persona nel gruppo, nessun prezzo nel listino) il cestino è a due tocchi, «Elimina?».
  - **Con conseguenze** il cestino apre la finestra di «eliminazione pesante» (design system), con i numeri veri da
    `GET /api/gruppi/{id}/eliminazione`: «38 persone escono dal gruppo», «1 dichiarazione da verificare si chiude» /
    «3 dichiarazioni da verificare si chiudono», «2 prezzi del listino per questo gruppo spariscono (Scuola nuoto, Nuoto
    adulti)» (con più di tre offerte i primi tre nomi e «e altre N»). Le righe a 0 non compaiono.
  - **Prezzi che spariscono.** I prezzi del gruppo nel listino (`offerta_prezzo.gruppo_id`, in tutte le stagioni) vanno con
    lui. Se un'offerta ha solo prezzi di quel gruppo e resterebbe senza prezzi, l'offerta si spegne e la finestra lo dice in una riga in
    più: «L'offerta «X» resta senza prezzi e si spegne: riaccendila dopo averle dato un prezzo.» (più offerte: «Le offerte «X»
    e «Y» restano senza prezzi e si spengono: riaccendile dopo aver dato loro un prezzo.»). Il backend lo ricava da
    `Listino.prezziDelGruppo` (campo `senzaPrezzi`). Un'offerta spenta può restare senza prezzi; per riaccenderla serve un prezzo
    (400 «Dai almeno un prezzo: un'offerta senza prezzi non si può accendere.»). La copia del listino salta le offerte senza
    prezzi e le elenca nella risposta (`saltate`), che la pagina mostra nel messaggio.
  - **`DELETE /api/gruppi/{id}`** senza conseguenze elimina e basta. Con conseguenze e senza `?pesante=true` risponde 409,
    così un gruppo che nel frattempo si è riempito non sparisce con due tocchi: «Non si può eliminare così: «X» ha 38 persone.»
    (o «… ha 2 prezzi nel listino.», o «38 persone e 2 prezzi nel listino») e sotto «Usa di nuovo il cestino: si apre la finestra
    con le conseguenze.» Con `pesante=true`, in una transazione: le appartenenze attive si chiudono (l'ultimo giorno è ieri, da
    oggi la persona non è più nel gruppo; chi era entrato oggi è tolto come se non fosse mai entrato, `AppartenenzaGruppo.chiudi`);
    tutte le appartenenze del gruppo, anche le chiuse di prima, ricevono la copia del nome e `gruppo_id` a null; i prezzi del
    gruppo si tolgono dal listino passando da `Listino.eliminaPrezziDelGruppo`; il gruppo si elimina; ordine e precedenze si
    ricompattano. Le persone escono tutte insieme.
  - **Ordine e precedenze.** L'ordine dell'elenco (la maniglia) è anche l'ordine delle precedenze: la precedenza di un gruppo
    è la sua posizione tra i gruppi con la precedenza accesa. `PUT /api/gruppi/ordine` `{"ids": [...]}` riordina e risponde con
    l'elenco (400 «L'ordine deve contenere, una volta sola, tutti i gruppi.»); sparisce `PUT /api/gruppi/precedenze`.
- **Stagioni** (`Societa.eliminaStagione`). Una stagione si elimina solo se nessuno la usa. Ogni modulo che la usa implementa
  `societa.UsiStagione` (listino, clienti, pianovasca, nell'ordine del messaggio), e `Societa` li interroga tutti: nessun
  ciclo tra moduli e niente «I dati non rispettano un vincolo». Gli usi sono due: ciò che si può togliere (le offerte nel
  listino) e lo storico (vendite dal listino, tesseramenti, esiti dei livelli). Nel messaggio prima ciò che si può togliere,
  poi lo storico, in fila con «, » e « e ». 409:
  - solo offerte: «Non si può eliminare: la stagione 2027/28 ha 12 offerte nel listino.» / «Svuota prima il listino: poi potrai
    eliminare la stagione.»
  - con qualcosa di storico: «Non si può eliminare: la stagione 2025/26 ha 12 offerte nel listino, 340 vendite dal listino e 214
    tesseramenti.» / «Resta nello storico: una stagione già usata non si elimina.»
  - Gli usi, al singolare e al plurale: «1 offerta nel listino», «1 vendita dal listino» (abbonamenti e addebiti con un'offerta
    della stagione), «1 tesseramento» (anche annullati), «1 esito dei livelli».
  - **Le regole di prezzo vanno con la stagione**: sono sue impostazioni. **Le chiusure del calendario restano**: non
    bloccano l'eliminazione e non si toccano.
  - `GET /api/stagioni` porta `eliminabile` e `motivoBlocco` solo a chi ha IMPOSTAZIONI (un DTO del controller: il record
    pubblico `Societa.Stagione` non cambia).
  - **Nome della stagione.** Una stagione che ha tesseramenti non si rinomina (i tesseramenti tengono il nome, non l'id): 409
    «Non si può rinominare: N tesseramenti usano il nome «2026/27».» / «Il nome resta: le date e la quota si possono cambiare.»
    (`UsiStagione.usiDelNome`). Così una stagione usata non diventa eliminabile cambiandole nome.
- **Offerte del listino** (`Listino.elimina`). Un'offerta mai venduta si elimina. Una venduta no, anche se le vendite sono
  annullate: 409 «Non si può eliminare: «Scuola nuoto» è stata venduta 34 volte.» («1 volta») / «Puoi spegnerla: chi l'ha
  comprata la tiene com'è, ma non si vende più.»; se è già spenta la seconda riga è «È già spenta: chi l'ha comprata la tiene
  com'è e non si vende più.» Il listino conta le vendite attraverso `listino.VenditeOfferte`, implementata da `clienti` (abbonamenti
  e carnet) e da `pagamenti` (addebiti).
  - **Ogni vendita si conta una volta.** Un abbonamento genera addebiti (le rate, il rimborso) che citano la stessa offerta:
    `VenditeAddebiti` conta solo quelli di origine ALTRO (gli «altri importi», per esempio una quota gara), perché gli altri sono
    già contati dall'abbonamento. Senza questa regola una vendita varrebbe due volte, o una per rata.
  - Se un vincolo del database ferma comunque l'eliminazione (`abbonamento_offerta_id_fkey`, `addebito_offerta_id_fkey`),
    `GestioneErrori` dice lo stesso: «Non si può eliminare: con questa offerta sono già state fatte delle vendite.» / «Puoi spegnerla:
    chi l'ha comprata la tiene com'è, ma non si vende più.» Il testo dice «spegnila», mai «disattivala».
  - `GET /api/listino` porta `eliminabile` e `motivoBlocco` solo a chi ha IMPOSTAZIONI.

## Conseguenze

- Eliminare serie, casse, offerte o stagioni già usate resta escluso. Per ammetterlo servirebbe una copia nello storico (come
  per i metodi di pagamento) e un nuovo ADR, dopo che `ricerca-dominio` avrà confrontato le pratiche del settore
  (numerazione delle ricevute, storico di cassa).
- Chi ha un passato si spegne: lo storico resta com'era e la cosa non viene più proposta per il nuovo.
- Ogni area nuova che adotta la regola scrive il suo messaggio di blocco nel servizio e lo mostra con `righeBlocco`.
