# 0018 · Pagamenti, listino, borsellino e ricevute (proposta)

**Stato.** Accettata il 23/10/2026, si costruisce un pezzo alla volta. Fatte: parti 1 (impostazioni della società e
stagioni), 2 (gruppi di clienti), 3–4 (listino e regole, con l'importo proposto alla vendita e il rimborso calcolato
al recesso), 5 (addebiti e pagamenti, 24/10/2026: vedi «Come è fatta la parte 5» in fondo), 7–9 (borsellino,
braccialetto, servizi a crediti con simulatore, 24/10/2026: vedi «Come sono fatte le parti 7–9»), 6 (chi non paga,
7/11/2026: vedi «Come è fatta la parte 6»), 10 (ricevute, 7/11/2026: vedi «Come è fatta la parte 10»), 11 (cassa, 8/11/2026: vedi «Come è fatta la parte 11»).

**Contesto.** Il gestionale deve servire a una società sportiva qualsiasi (vedi «Principi» in `docs/architettura.md`):
listino, sconti, rate, rimborsi e regole cambiano da una società all'altra e da una stagione all'altra. Le regole della
Leone XIII Sport (`docs/gestionale-attuale.md`) sono l'esempio e i dati demo. Ogni automatismo che limita è
un'opzione con i suoi tempi, o un permesso.

**Proposta.**

1. **Impostazioni della società**: nome, stagioni (data di inizio e fine, una per anno), quota associativa (importo,
   una per persona per stagione anche con più attività, rimborsabile sì/no). Oggi stagione e quota sono nel codice:
   diventano dati.
2. **Gruppi di clienti**: etichette date alle persone (es. «Alunni dell'Istituto»). Servono per prezzi diversi e per
   la precedenza nei rinnovi. Prendono il posto della «categoria» del vecchio gestionale (ADR 0014).
3. **Listino** per stagione: offerte di tipo *corso* (prezzo per frequenza: mono, bi, tri, open), *pacchetto di
   lezioni*, *abbonamento a ingressi liberi* (per frequenza o fascia), *pacchetto di ingressi* (numero, validità,
   fascia oraria), *ingresso singolo*, *quota fissa* (es. agonistica), più cauzione del braccialetto e prezzo del
   credito. Prezzi facoltativi per gruppo di clienti.
4. **Regole configurabili**, ciascuna accesa o spenta: riduzione per iscrizione a stagione iniziata (tabella mese →
   percentuale), piano di rate (quante, quanto all'iscrizione, scadenze), recesso (tabella data → percentuale di
   rimborso, dove va il rimborso), sospensione per malattia (soglia in settimane, termine per il certificato),
   cambio di attività (differenza da pagare o da accreditare). Il gestionale **propone** l'importo; chi ha il permesso
   lo può cambiare, con il motivo.
5. **Addebiti e pagamenti**: ogni iscrizione, quota o vendita crea un *addebito* (cosa si deve, con scadenza, anche a
   rate); un *pagamento* lo salda tutto o in parte, anche con più metodi (contanti, bancomat, carta, Satispay,
   bonifico, borsellino). I metodi sono un elenco modificabile. Il bonifico si registra come «da incassare» e si segna
   incassato a mano. Chi paga può essere il tutore: il saldo si vede per persona e per famiglia.
6. **Chi non paga**: passi impostabili dopo la scadenza (es. dopo N giorni promemoria email; dopo M giorni il tornello
   avvisa; dopo K giorni dà rosso). Un nuovo controllo del tornello (ADR 0009) con questi parametri, spento di base;
   i promemoria riusano quelli delle scadenze (ADR 0013).
7. **Borsellino** per persona, con due saldi: *credito in euro* (rimborsi per recesso, sospensione o cambio; usabile
   per pagare; scadenza impostabile; trasferibile a un familiare) e *crediti per i servizi* (phon, docce; si ricaricano
   pagando; valore del credito e ricarica minima impostabili). Ogni movimento è registrato: ricarica, consumo,
   accredito, trasferimento, rimborso, scadenza.
8. **Braccialetto**: alla consegna l'addebito della cauzione; alla riconsegna il rimborso della cauzione e dei crediti
   per i servizi; se smarrito la cauzione resta e i crediti si perdono (oppure passano al nuovo braccialetto: regola
   impostabile).
9. **Servizi a crediti** (phon, docce): ogni servizio ha un prezzo in crediti e utilizzi gratuiti al giorno (es. la
   prima doccia). Le macchine, registrate come dispositivi con una chiave propria, chiamano il server con il codice
   del braccialetto: il server risponde sì/no e scala i crediti. Il protocollo delle macchine vere è da verificare
   (marca e modello): se non si può cambiare, serve un adattatore. Alla Leone le centraline sono Romano Control
   Systems (foto descritte in `docs/gestionale-attuale.md`); tre strade, da scegliere quando sapremo dove sta il credito:
   a) la centralina chiede a un server: il gestionale espone l'API, o un adattatore traduce il protocollo Romano;
   b) il credito è scritto sul braccialetto: il gestionale lo scrive con un lettore/scrittore in segreteria, con le
   chiavi e il formato di Romano, oppure per le ricariche si tiene affiancato il loro programma;
   c) si sostituisce l'elettronica della centralina con una nostra (lettore RFID e relè per prese ed elettrovalvole)
   che parla con l'API del gestionale. Il simulatore serve in tutti e tre i casi. La strada b), con il formato del braccialetto
   ancora da conoscere, è preparata nell'ADR 0025.
10. **Ricevute**: numerate per serie (es. ricevute, fatture) con numerazione che riparte a ogni stagione e formato
    impostabile; PDF A4 da stampare o mandare per email; intestate a chi paga (spesso il genitore) con il beneficiario
    e i codici fiscali, come servono per detrazioni e welfare. La fattura: dati del cliente e numerazione propria;
    l'invio allo SdI (fattura elettronica) è da decidere con chi la emette oggi.
11. **Cassa**: apertura con fondo cassa, chiusura (alla Leone la fa chi è in segreteria, a fine turno) con totali per
    metodo, contanti attesi e contati, differenza; elenco degli incassi esportabile per l'amministrazione. Da
    impostare: una cassa unica o una per postazione (la Leone ha due postazioni), fondo cassa fisso o scritto a ogni
    apertura, chiusura per turno o per giornata.
12. **Precedenza nei rinnovi**: periodo di prelazione per la stagione nuova; le richieste di iscrizione vanno in coda
    in ordine di arrivo, prima chi era iscritto nella stagione precedente e i gruppi con precedenza (es. alunni), poi
    gli altri. Riusa la lista d'attesa (ADR 0015).

**Ordine proposto.** 1–2 (impostazioni e gruppi) → 3–4 (listino e regole, con l'importo proposto all'iscrizione) →
5 (addebiti e pagamenti) → 7–9 (borsellino, braccialetto, servizi, con un simulatore della macchina) → 6 (chi non
paga) → 10–11 (ricevute e cassa) → 12 (rinnovi).

**Come è fatta la parte 5.**
- Modulo `pagamenti`. La vendita di un abbonamento (dal listino: nelle rate della regola, anche con l'importo
  scontato), il tesseramento (la quota della stagione) e le offerte «altro importo» creano gli addebiti. Gli addebiti
  si possono anche scrivere a mano (es. cuffia).
- Un pagamento salda uno o più addebiti, anche di più familiari (genitori e fratelli dalla scheda), nell'ordine scelto
  (di base prima quelli che scadono prima); può essere un acconto, ma non più di quanto si deve. I metodi possono
  essere più d'uno insieme. Quelli «da incassare» (bonifico) restano nell'elenco «Da incassare» finché qualcuno non li
  segna arrivati.
- Permessi: «Pagamenti» (registrare pagamenti e addebiti, incassi; segreteria e coordinatori), «Acconti e pagamenti
  misti» (pagare solo una parte, dividere tra più metodi; senza, si paga per intero con un metodo solo; di partenza a
  segreteria e coordinatori) e «Annullare pagamenti» (coordinatori), sempre con il motivo. Un addebito pagato non si
  annulla: prima si annulla il pagamento. I metodi di pagamento sono un'impostazione della società.
- Più familiari insieme (es. due fratelli): impostazione della società «pagamenti di famiglia»: sempre un pagamento
  unico, sempre uno per persona (i metodi si consumano in ordine, il primo copre il primo pagamento), oppure si sceglie
  ogni volta (di partenza). Così le ricevute (parte 10) potranno essere una per famiglia o una per figlio.
- Il tesseramento ricorda la data in cui la quota è stata pagata tutta (evento `Pagamenti.Saldato`, ascoltato dal modulo
  clienti); con «pagatoIl» indicato a mano (es. dati importati) non si crea l'addebito.
- Recesso («Termina oggi» con «Recesso»): gli addebiti non pagati si annullano, quelli pagati in parte scendono al
  pagato; il rimborso secondo la regola si calcola su quanto è stato pagato davvero e, finché non c'è il borsellino
  (parte 7), si registra come credito (addebito negativo, origine RIMBORSO).
- Pagina «Incassi»: pagamenti di un giorno o periodo con i totali per metodo, esportazione CSV, pagamenti da incassare.
  La chiusura di cassa vera (fondo cassa, contati) è la parte 11. Da ottobre 2026 la pagina ha tre viste a scelta
  (cassa del giorno, periodo, registro) e mostra già il riquadro della chiusura: fondo e contati si scrivono e la
  differenza si calcola sul momento, ma non si salva niente e i pulsanti dicono che la funzione è in sviluppo, finché
  la segreteria non spiega come si chiude la cassa oggi. (Superato dalla parte 11, fatta configurabile senza aspettare.)

**Come sono fatte le parti 7–9.**
- Il credito sta **sul server**, non sul braccialetto (che porta solo il numero di serie, come quello della Leone): i
  saldi sono della **persona**, e la segreteria ricarica dall'anagrafica senza dover leggere il braccialetto.
- Borsellino nel modulo `pagamenti` (classe `Borsellino`): movimenti in euro e in crediti. Gli euro in entrata hanno
  una scadenza (regola «validità») e le uscite consumano prima quelli che scadono prima; ogni notte gli euro scaduti
  escono. Il borsellino è anche un metodo di pagamento: paga chi paga (anche un genitore per il figlio). Annullando un
  pagamento tornano gli euro spesi, e una ricarica annullata toglie i crediti comprati.
- Ricarica: un addebito (origine RICARICA) pagato subito, poi i crediti; importo minimo e multiplo del valore di un
  credito (regole). Trasferimento di euro ai familiari (se la regola lo permette); restituzione in denaro (se la regola
  lo permette, e con il permesso «Rimborsi e correzioni del borsellino»); correzioni a mano con il motivo. Il rimborso
  per recesso «nel borsellino» ora ci va davvero.
- Soldi che escono dalla cassa (cauzione restituita, credito in denaro): tabella `uscita`, nella pagina «Incassi».
- Braccialetto: la cauzione (regola, 0 = nessuna) è un addebito alla consegna (si può non addebitare); «Riconsegnato»
  (stato RESTITUITO, il codice torna libero) restituisce la cauzione pagata in denaro, nel borsellino o la trattiene, e
  rimborsa i crediti se era l'ultimo braccialetto attivo; «Smarrito» li fa perdere con la regola «crediti persi».
- Modulo `servizi`: servizi (crediti per uso, usi gratuiti al giorno, minuti), dispositivi (tipo SERVIZIO o SALDO) con
  una chiave di cui si conserva solo l'impronta SHA-256, utilizzi (anche quelli negati). API delle macchine in
  `/api/macchine/{id}` con l'intestazione `X-Chiave-Dispositivo`, fuori da sessione e CSRF. Il simulatore usa la stessa
  logica e segna gli utilizzi come simulati. Per le macchine vere resta da scegliere tra le tre strade del punto 9.

**Come è fatta la parte 6.**
- *Arretrato*: per ogni persona, il totale ancora da pagare degli addebiti già scaduti (non annullati, non crediti),
  la scadenza più vecchia e quante voci sono (`Pagamenti.arretrati`, `Pagamenti.arretratoDi`). Si guarda la persona a
  cui è intestato l'addebito, non chi paga.
- *Tornello*: la regola «Pagamenti scaduti» (`PAGAMENTI`, migrazione V26), spenta di base, con tre parametri: dopo
  quanti giorni dalla scadenza avvisa (7), dopo quanti dà rosso (30; vuoto = solo l'avviso) e sopra quale importo
  conta (0 €). L'avviso fa passare e dice al tornellista quanto c'è da pagare e da quando arriverà il rosso. Chi ha il
  golden pass non è controllato. Al banco chi è fermo per questo sta tra «Abbonamenti e quote», con «Fai pagare».
- *Scadenze*: una voce «Da pagare … €» per persona, filtro «Pagamenti», anche per chi non frequenta più.
- *Promemoria*: il tipo «Pagamento scaduto», spento di base: il primo N giorni dopo la scadenza, poi ogni N giorni, al
  massimo M volte per lo stesso arretrato (se si paga la voce più vecchia e ne resta un'altra, si riparte). Nel testo
  si può usare `{importo}`.

**Conseguenze.** Molte tabelle nuove e un modulo `pagamenti`; gli abbonamenti attuali (ADR 0009) diventano righe del
listino vendute a una persona. Le parti 10–11 dipendono da risposte ancora aperte (formato delle ricevute, fattura
elettronica, cosa serve all'amministrazione).

## Come è fatta la parte 10 (ricevute)

- Modulo `ricevute` (dipende da pagamenti, identita, societa, notifiche). Migrazione V27: `serie_ricevute`,
  `impostazioni_ricevute` (una riga), `ricevuta`.
- **Serie e numerazione.** Più serie (di base «Ricevute», `{stagione}-{numero}` a 4 cifre, che riparte ogni stagione;
  «Fatture», spenta). Il formato usa `{stagione}`, `{anno}` e `{numero}` (obbligatorio); si riparte ogni stagione, ogni
  anno o mai. Il numero si prende bloccando la riga della serie, non si riusa mai (nemmeno dopo un annullamento) e il
  «parti da» si può cambiare solo finché il periodo è vuoto.
- **Fotografia.** Una ricevuta salva intestatario, beneficiari, righe e dicitura come erano al momento (jsonb): se poi
  cambia un indirizzo o un prezzo, la ricevuta già fatta non cambia. Per correggerla si annulla (con il motivo) e se ne
  fa una nuova, che prende il numero successivo; la vecchia resta, barrata, con il rimando alla nuova.
- **Una o più.** Intestata a chi paga (con i nomi dei figli) o a chi frequenta; quando un genitore paga per più figli
  si chiede (o si sceglie una volta per tutte) se farne una per famiglia o una per figlio.
- **Dopo il pagamento.** Come dicono le impostazioni: pannello con stampa, PDF ed email; stampa subito; oppure niente
  (si fa su richiesta, anche dallo storico). Se c'è l'email di chi paga e l'opzione è accesa, parte in allegato.
- **Annullare il pagamento** annulla da sé le sue ricevute (evento `Pagamenti.PagamentoAnnullato`).
- **PDF** con OpenPDF (A4): intestazione della società, righe, totale, riquadro per la marca da bollo sopra la soglia,
  dicitura (con `{beneficiario}` e `{nascita}`), riga per la firma; le annullate hanno la scritta «ANNULLATA».
- Dicitura, soglia e importo del bollo di base sono indicativi: vanno confermati con l'amministrazione.

## Come è fatta la parte 11 (cassa)

- Modulo `cassa` (dipende da pagamenti, identita, societa). Migrazione V28: `cassa`, `impostazioni_cassa` (una riga),
  `sessione_cassa`, `movimento_cassa`; in più `metodo_pagamento.contanti` (quali metodi finiscono nel cassetto: non
  più riconosciuti dal nome) e `cassa_id` su `pagamento` e `uscita`.
- Fatta senza aspettare come si chiude oggi la cassa alla Leone: le tre scelte dell'elenco sopra sono impostazioni.
  **Casse**: una sola, o una per postazione (ogni computer sceglie la sua nella pagina Incassi; la scelta resta sul
  dispositivo e va con ogni richiesta nell'intestazione `X-Cassa`; senza, o con una cassa che non c'è più, la
  principale). **Fondo**: fisso (si lascia sempre lo stesso, il resto si versa), riportato (si riparte da quello
  lasciato) o contato a ogni apertura (si vede se torna con quello lasciato). **Chiusura**: per turno (chi entra apre
  la cassa) o per giornata (si apre da sola alla prima operazione o alla chiusura).
- **Sessione.** Conta quello che passa dalla cassa tra la chiusura prima (esclusa) e la sua: niente buchi né doppioni,
  e i pagamenti fatti a cassa chiusa finiscono nella sessione dopo, segnati. Attesi = fondo + contanti entrati −
  contanti restituiti − versamenti − spese + aggiunte. Alla chiusura: contati (anche per tagli), differenza, quanto si
  lascia (il resto è «versato») e una nota; se nel frattempo gli attesi sono cambiati (un pagamento arrivato mentre si
  contava) la chiusura si rifiuta. I totali per metodo si fotografano (jsonb): annullare dopo un pagamento non cambia
  una chiusura fatta.
- **Movimenti a mano**: versamento (banca, cassaforte), spesa pagata con la cassa, aggiunta (monete); si annullano
  finché la sessione è aperta.
- **Riaprire** solo l'ultima chiusura di una cassa e solo se dopo non si è aperto niente, con il motivo e il permesso
  «Annullare pagamenti»; resta la nota con chi e quando.
- Pagina Incassi: la cassa di oggi nella colonna di lato; per un giorno passato le sue chiusure; una quarta vista
  «Chiusure di cassa» con le differenze del periodo e il CSV. Riepilogo PDF (OpenPDF) da firmare. Impostazioni ›
  Cassa. Dati demo: le chiusure delle ultime due settimane.
- **I controlli** (9/11/2026, V29), dopo un confronto con le pratiche del settore e con programmi di cassa diffusi
  (Square, Odoo, Lightspeed, Mangomint); tutti configurabili:
  - *conta alla cieca* (accesa di base): di una cassa aperta non si mostrano gli importi attesi, ma solo il fondo, quanti
    pagamenti e i movimenti a mano; il confronto (`POST /casse/{id}/confronta`) li mostra e tiene il primo conteggio,
    così un riconteggio resta visibile nello storico e nel PDF;
  - *differenza tollerata* (5 € di base, 0 = ogni differenza): oltre, o con un riscontro che non torna, la nota è
    obbligatoria;
  - *controllo* (NESSUNA / OLTRE / TUTTE, di base OLTRE): la chiusura resta «da controllare» finché chi può annullare i
    pagamenti, e non chi l'ha chiusa, la segna come controllata (con una nota); numero sulla voce Incassi;
  - *riscontro con il POS*: per i metodi segnati (di base il bancomat) si scrive il totale dello scontrino di chiusura;
    atteso, scritto e differenza si fotografano nella chiusura;
  - *avviso* se una cassa resta aperta più di N ore (14 di base);
  - *spese con la cassa* sì o no;
  - *cassaforte e banca* (spento di base): quello versato alle chiusure (più i versamenti durante il turno) resta in
    cassaforte finché non si registra il versamento in banca con il giorno e la distinta; una chiusura già in banca non
    si riapre, un versamento annullato rimette le chiusure in cassaforte.
  - Fuori: il registratore telematico (la chiusura è un documento interno) e il nascondere i totali dei contanti nelle
    altre viste di Incassi.

