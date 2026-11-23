# 0030 · Eliminare sempre, con la copia nello storico

**Stato:** accettata (applicata a metodi di pagamento, gruppi, servizi e macchine; postazioni, mansioni e casse seguono nel lotto 5). Sostituisce in parte l'ADR 0029: la regola «si elimina solo ciò che non è mai stato usato».

## Contesto

L'ADR 0029 diceva: una cosa di configurazione si elimina solo se non è mai stata usata; se lo storico la usa senza copia,
l'eliminazione si ferma e si spegne. La regola ha un difetto. Una macchina non più installata, che ha un solo utilizzo
nello storico, non si può più togliere dall'elenco: gli elementi inutili si accumulano per sempre, spenti ma presenti.

Lo scopo vero della regola era un altro: non perdere le informazioni importanti (anagrafiche, passaggi al tornello,
pagamenti, ricevute). Non era tenere in vita gli oggetti di configurazione. I metodi di pagamento (ADR 0028, V33) e i
gruppi (V35) lo hanno già mostrato: se lo storico porta una copia di ciò che serve a riconoscere la cosa, la cosa si
può eliminare senza perdere nulla.

## Decisione

- **Una cosa di configurazione si elimina sempre**, anche se è stata usata. Lo storico tiene una **copia** di ciò che
  serve a riconoscerla (il nome; dove serve, anche un riferimento facoltativo con `on delete set null`).
- **La copia sta nel database**, nella riga dello storico, non in file esterni. Motivi: i backup la portano con sé, si
  può cercare, i resoconti la leggono con una query sola, e i dati personali restano sotto le stesse protezioni.
- **L'eliminazione si ferma solo se c'è qualcosa ancora in corso che la usa**: una cassa aperta, pagamenti da incassare,
  abbonamenti attivi. Il messaggio è su due righe, nel formato del design system («Messaggi di blocco»): cosa non si
  può fare, e cosa si può fare invece. Il DTO porta `eliminabile` e `motivoBlocco`, calcolati solo per chi ha il
  permesso di modificare.
- **Restano eccezioni** dove c'è un vincolo esterno o un calcolo che dipende dall'oggetto:
  - **le serie delle ricevute che hanno emesso** (numerazione fiscale: prima una ricerca sulle pratiche del settore,
    con `ricerca-dominio`, poi si decide);
  - **le offerte vendute** (le rate e il recesso di un abbonamento si calcolano dall'offerta): si spengono;
  - **le stagioni usate** (sono il periodo a cui i dati appartengono): non si eliminano.
- **Un'eliminazione rara che tocca molti dati** usa la finestra di «eliminazione pesante» del design system, non il
  doppio tocco (come per i gruppi con persone e prezzi).
- Valgono ancora, dall'ADR 0029: il controllo sta nel servizio e non in un vincolo del database; ciò che il sistema
  richiede non si elimina (il borsellino, la cassa principale, l'ultima serie di ricevute accesa); ogni elenco
  riordinabile ha il suo `PUT …/ordine`.

### Già conformi

- **Metodi di pagamento** (ADR 0028, V33): il pagamento tiene la copia del nome del metodo.
- **Gruppi di clienti** (lotto 3, V35): l'appartenenza chiusa tiene la copia del nome del gruppo.

### Applicazione nel lotto 4: servizi e macchine

- **Migrazione V36** (`utilizzo`). Aggiunge le copie `dispositivo_nome` (obbligatoria) e `servizio_nome`, valorizzate per le
  righe già presenti. `dispositivo_id` diventa facoltativo e le chiavi esterne verso `dispositivo` e `servizio` diventano
  `on delete set null`: eliminata la macchina o il servizio, il riferimento si svuota e la copia resta. Un trigger
  (`utilizzo_copia`) compila le copie per chi inserisce senza (i dati demo); il codice dell'applicazione le scrive sempre.
- **Anche `dispositivo.servizio_id`** diventa `on delete set null` (e cade il `check` che lo voleva pieno per le macchine
  di tipo servizio): un servizio eliminato lascia le sue macchine **senza servizio**.
- **Macchina senza servizio.** Quando qualcuno passa il braccialetto risponde «Macchina non configurata» con esito
  `SERVIZIO_SPENTO`, senza scalare crediti, e l'utilizzo si registra lo stesso con quell'esito. Nella pagina la riga ha il tag ambra «Senza servizio» e il
  foglio permette di sceglierne uno.
- **Letture dello storico.** Gli utilizzi (resoconti, «utilizzi oggi», scheda cliente) leggono il nome dalla copia: reggono
  una macchina o un servizio eliminati.
- **Nessun blocco.** Gli utilizzi sono registrazioni di fatti già avvenuti: non c'è niente «in corso» legato a servizi e
  macchine (i lettori del saldo non scrivono utilizzi; i crediti stanno nel borsellino della persona, non sul servizio). Quindi
  `DELETE /api/servizi/{id}` e `DELETE /api/dispositivi/{id}` rispondono sempre 204 (404 se non esiste); `eliminabile`
  resta vero e `motivoBlocco` vuoto. Il cestino è a due tocchi, e dopo l'eliminazione la pagina dice cosa resta: ««Phon 2» eliminata: i suoi 3.410 utilizzi restano nello storico.»
  (per un servizio aggiunge che le sue macchine restano senza servizio).
- I testi «è stato usato N volte», «ha N utilizzi», «ha 3 macchine» e i ripieghi di `GestioneErrori` sulle chiavi di
  `utilizzo` non servono più.

### Da fare

- **Lotto 5, postazioni e mansioni.** I turni passati tengono il nome della postazione e della mansione.
- **Casse da rivedere.** Una cassa si potrà eliminare anche con sessioni passate, purché non sia aperta, con il nome
  copiato in sessioni e pagamenti. Oggi vale ancora la regola dell'ADR 0029.

## Conseguenze

- Gli elenchi di configurazione non si riempiono più di cose morte: ciò che non serve più si toglie, e lo storico resta
  leggibile con i nomi di allora.
- Ogni area che adotta la regola aggiunge nella sua tabella storica la copia del nome (e il riferimento facoltativo) con
  una migrazione, e legge sempre dalla copia.
- Chi guarda lo storico non può più risalire, dal riferimento, alla macchina o al gruppo eliminati: ha il nome, non la
  configurazione di allora.
- Il nome nello storico è quello del momento della registrazione: se si rinomina una macchina, un servizio o un gruppo,
  gli utilizzi e le appartenenze di prima continuano a mostrare il nome vecchio. È voluto: lo storico racconta com'era.
