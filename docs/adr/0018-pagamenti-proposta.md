# 0018 · Pagamenti, listino, borsellino e ricevute (proposta)

**Stato.** Accettata il 23/10/2026, si costruisce un pezzo alla volta. Fatte: parti 1 (impostazioni della società e
stagioni), 2 (gruppi di clienti), 3–4 (listino e regole, con l'importo proposto alla vendita e il rimborso calcolato
al recesso), 5 (addebiti e pagamenti, 24/10/2026: vedi «Come è fatta la parte 5» in fondo).

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
   (marca e modello): se non si può cambiare, serve un adattatore.
10. **Ricevute**: numerate per serie (es. ricevute, fatture) con numerazione che riparte a ogni stagione e formato
    impostabile; PDF A4 da stampare o mandare per email; intestate a chi paga (spesso il genitore) con il beneficiario
    e i codici fiscali, come servono per detrazioni e welfare. La fattura: dati del cliente e numerazione propria;
    l'invio allo SdI (fattura elettronica) è da decidere con chi la emette oggi.
11. **Cassa**: apertura con fondo cassa, chiusura a fine giornata con totali per metodo, contanti attesi e contati,
    differenza; elenco degli incassi esportabile per l'amministrazione.
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
- Permessi: «Pagamenti» (registrare pagamenti e addebiti, incassi; segreteria e coordinatori) e «Annullare pagamenti»
  (coordinatori), sempre con il motivo. Un addebito pagato non si annulla: prima si annulla il pagamento. I metodi di
  pagamento sono un'impostazione della società.
- Il tesseramento ricorda la data in cui la quota è stata pagata tutta (evento `Pagamenti.Saldato`, ascoltato dal modulo
  clienti); con «pagatoIl» indicato a mano (es. dati importati) non si crea l'addebito.
- Recesso («Termina oggi» con «Recesso»): gli addebiti non pagati si annullano, quelli pagati in parte scendono al
  pagato; il rimborso secondo la regola si calcola su quanto è stato pagato davvero e, finché non c'è il borsellino
  (parte 7), si registra come credito (addebito negativo, origine RIMBORSO).
- Pagina «Incassi»: pagamenti di un giorno o periodo con i totali per metodo, esportazione CSV, pagamenti da incassare.
  La chiusura di cassa vera (fondo cassa, contati) è la parte 11.

**Conseguenze.** Molte tabelle nuove e un modulo `pagamenti`; gli abbonamenti attuali (ADR 0009) diventano righe del
listino vendute a una persona. Le parti 10–11 dipendono da risposte ancora aperte (formato delle ricevute, fattura
elettronica, cosa serve all'amministrazione).
