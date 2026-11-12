# 0026 · Campagne di iscrizione: riconferme, graduatoria, assegnazione dei turni, area per le famiglie

**Stato:** proposta (novembre 2026), in attesa dei mockup

## Contesto

L'ADR 0018 (parte 12) prevedeva «la precedenza nei rinnovi» come ultimo pezzo dei pagamenti. Parlandone è diventata
una cosa più grande. Alla Leone l'inizio della stagione è il momento più difficile: il gestionale attuale non permette di
mettere un limite alle richieste, le famiglie non sanno a che punto sono, e la segreteria tiene a mano chi ha diritto al
posto, chi paga entro quando e chi aspetta.

Come si comporta (o vorrebbe comportarsi) la Leone:

- chi ha frequentato l'anno prima ha il posto garantito **nel livello**, non nel giorno e orario. A fine stagione il
  bambino passa al livello successivo o continua nello stesso (mai «bocciato»); un istruttore può farlo passare anche
  durante l'anno;
- i corsi sono aperti a tutti, ma a parità di situazione gli alunni della scuola Leone XIII («Leoniani») hanno la
  precedenza, anche se hanno fatto richiesta dopo;
- quando una richiesta è accettata si paga entro pochi giorni, e i Leoniani potrebbero avere più tempo (es. 7 giorni
  invece di 3);
- per il resto vale l'ordine di arrivo; la famiglia deve vedere la sua posizione e, una volta accettata, quanto manca
  alla scadenza e come pagare;
- i posti per i nuovi di un corso sono quelli decisi da amministrazione e istruttori, meno le riconferme;
- molte famiglie sono disposte ad aspettare anche uno o due mesi dopo l'inizio della stagione.

Come sempre il gestionale deve servire a qualsiasi società: le regole della Leone sono i valori di esempio, tutto il
resto è un'impostazione.

## Decisione

### Gruppi di clienti con vantaggi

Si estendono i **gruppi di clienti** che esistono già (ADR 0018, parte 2: oggi servono per i prezzi). Una pagina simile a
quella dei ruoli dello staff, ma i gruppi dicono le *condizioni* di una persona, non cosa può fare nel gestionale. Ogni
gruppo può avere:

- una **precedenza** (posizione nell'ordine dei gruppi; nessuna = nessuna precedenza);
- **giorni per pagare** dopo l'accettazione (vuoto = quelli di base);
- **dichiarabile dalle famiglie** (es. «frequenta la scuola Leone XIII»): la famiglia lo segna nella richiesta, la
  segreteria lo **verifica**. Finché non è verificato non dà precedenza.

Il vantaggio è della persona che frequenta (il bambino), non dell'account che fa la richiesta (il genitore).
L'appartenenza ha già le date di inizio e fine. Con più gruppi vale il vantaggio migliore di ciascun tipo. Chi non è in
nessun gruppo ha le regole di base: «Esterno» non serve come gruppo, ma si può creare.

### Livelli dei corsi

- Ogni corso può indicare il suo **livello successivo** (es. Delfino Giallo → Delfino Verde).
- **Esito di fine stagione** per ogni iscritto: «passa al livello successivo» o «continua nello stesso livello». Lo
  decidono istruttori e coordinatori (permesso «Livelli»).
- **Passaggio durante l'anno**: «Passa al livello successivo» in qualunque momento. Il bambino tiene il suo turno finché
  non c'è posto nel livello nuovo, e intanto è in lista d'attesa lì, con la precedenza di chi è passato.

### La campagna

Una o più campagne per stagione, ognuna con un nome e i suoi corsi (es. «Piscina 2027/28»). Le fasi hanno le loro date
e si possono sovrapporre.

1. **Riconferme.** Per ogni iscritto il gestionale prepara la proposta per la stagione nuova, nel livello che dice
   l'esito. La famiglia conferma entro una data, indicando gli stessi giorni dell'anno prima o nuove preferenze di
   turno. Chi non conferma libera il posto.
2. **Raccolta delle richieste nuove**, dall'area famiglie o al banco: corso, turni che vanno bene (in ordine, oppure
   «qualsiasi turno»), facoltativamente «nello stesso turno di mio fratello/mia sorella» e i gruppi da verificare.
   Nessun pagamento. Il limite non blocca la raccolta ma la rende chiara: «posti per i nuovi: 12 · sei 18° · in lista
   d'attesa». Facoltativo: la raccolta di un corso si chiude dopo un certo numero di richieste oltre i posti.
3. **Graduatoria**, in uno dei due modi (scelta della società):
   - *a data fissa*: le richieste arrivate entro una data si ordinano con le precedenze e la graduatoria si pubblica
     quel giorno; dopo vale l'ordine di arrivo, e una posizione può solo migliorare;
   - *sempre aggiornata*: le precedenze valgono sempre e la posizione mostrata è «indicativa».

   L'ordine dei criteri si sceglie: riconferma, fratello o sorella già iscritti (facoltativo), gruppi con precedenza
   nel loro ordine, ora di arrivo.
4. **Assegnazione dei turni** (sotto: l'ottimizzatore). Il gestionale propone a quale turno va ogni richiesta. La
   segreteria controlla, ritocca e pubblica (o si pubblica da sola).
5. **Posto offerto e pagamento**: la famiglia vede il turno, la scadenza (giorni di base o del gruppo) e come pagare;
   nasce l'addebito. Due accettazioni distinte, ciascuna manuale o automatica: *la richiesta che diventa posto offerto*
   e *il pagamento che diventa iscrizione*. Con l'iscrizione nascono l'iscrizione al turno (ADR 0008) e, dove serve,
   l'abbonamento.
6. **Scorrimento**: una proposta scaduta o rifiutata libera il posto; il gestionale cerca chi viene dopo in graduatoria
   e ha indicato quel turno, e prepara la proposta (da solo o con un clic). I giri continuano finché ci sono posti e
   richieste.
7. **Attesa lunga**: chi non è entrato resta in attesa anche a stagione iniziata, collegato alle liste d'attesa per
   turno (ADR 0015). La famiglia può scrivere «aspetto fino al…» e cambiare i turni che le vanno bene; alla data esce da
   sola, con un avviso.

**Stati di una richiesta:** inviata → in graduatoria (con la posizione) → posto offerto (turno, paga entro…) → iscritta;
oppure scaduta, rinunciata, in attesa fino al…, chiusa. Ogni passaggio finisce nello storico con chi l'ha fatto.

### L'ottimizzatore

Si scorre la graduatoria: a ognuno il turno più alto nelle sue preferenze che ha ancora posto, così nessuno è scavalcato
da chi sta più in basso. Poi, per chi è rimasto fuori, si cerca di spostare chi è già sistemato su un altro dei turni che
aveva indicato, per liberargli un posto («Luca va bene martedì o giovedì, Sara solo martedì: Luca passa a giovedì, Sara
entra»). Nessuno perde il posto né finisce in un turno che non ha scelto. A parità di risultato si preferiscono le prime
scelte, e i fratelli nello stesso turno se lo hanno chiesto. Tecnicamente è un abbinamento (bipartite matching) che
rispetta la priorità: cammini aumentanti presi nell'ordine della graduatoria, poi a costo minimo sulle preferenze. La
proposta spiega ogni spostamento, e la segreteria può bloccare a mano un'assegnazione prima di ricalcolare.

### Impostazioni (con i valori di base)

| Scelta | Di base | Altre possibilità |
| --- | --- | --- |
| Graduatoria | a data fissa | sempre aggiornata |
| Accettazione della richiesta | manuale | automatica |
| Conferma dopo il pagamento | automatica | manuale |
| Giorni per pagare | 3 | per gruppo (es. Leoniani 7) |
| Proposta scaduta senza pagare | la richiesta esce | torna in fondo alla graduatoria |
| Rifiuto di un turno che si era indicato | resta in graduatoria, fuori da quel turno | perde la posizione; può chiedere un cambio di turno |
| Fratelli nello stesso turno | preferenza possibile | spenta |
| Precedenza per chi ha un fratello già iscritto | spenta | accesa, con il suo posto nell'ordine dei criteri |
| Chiusura della raccolta | mai | dopo N richieste oltre i posti |

### Area per le famiglie

- **Registrazione da soli**: email verificata, poi codice fiscale obbligatorio e controllato (anche il carattere di
  controllo) per sé e per i figli. Se il codice fiscale è già in anagrafica non si crea una persona nuova: il
  collegamento (account → persona, genitore → figlio, ADR 0014) lo conferma la segreteria, perché conoscere un codice
  fiscale non basta.
- **Account inattivi**: dopo N mesi senza accessi e senza iscrizioni un avviso per email, dopo altri M giorni l'account
  si disattiva. Le persone restano finché servono (ricevute, obblighi di legge).
- Le famiglie vedono le richieste con lo stato, la posizione, il posto offerto con il conto alla rovescia e come pagare;
  possono confermare le riconferme, rinunciare, cambiare preferenze e «aspetto fino al».
- **Come pagare**: di persona in segreteria (indirizzo e orari, un dato nuovo della società); con un bonifico, con il QR
  europeo «EPC» (beneficiario, IBAN, importo, causale con un codice unico) e accanto gli stessi dati con il tasto
  «Copia». In Italia non tutte le app bancarie leggono il QR: i dati da copiare non sono un ripiego. Il bonifico resta
  «da incassare» finché la segreteria non lo segna (più avanti: import dell'estratto conto).

### In che ordine

- **A.** Gruppi con vantaggi, livelli ed esiti, campagna, graduatoria, ottimizzatore, proposte e scorrimento, dal lato
  della segreteria (le richieste si possono inserire al banco).
- **B.** Area per le famiglie: registrazione, richieste, stato, pagamento con bonifico.
- **C.** Dopo, se serve: addebito diretto SEPA (codice creditore della società, mandato, file per la banca, insoluti),
  pagamento con carta, import dell'estratto conto.

## Conseguenze

- La parte 12 dell'ADR 0018 diventa questo ADR.
- Le richieste di cambio di turno delle famiglie (ADR 0015, «dall'area clienti») arrivano con la parte B.
- I dati dei minori passano anche dall'area famiglie: niente dati veri nel repository, nei test o negli screenshot;
  consensi e informativa con la registrazione.
- L'ottimizzatore è codice da provare bene: casi piccoli verificati a mano, e proprietà controllate su casi casuali
  (nessuno scavalcato da chi è più in basso, nessuno in un turno non scelto, posti mai superati).
