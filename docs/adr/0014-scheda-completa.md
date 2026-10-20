# 0014 · Scheda cliente completa: anagrafica, tutori e famiglia, consensi

**Contesto.** Nel vecchio gestionale la scheda ha anagrafica completa, nucleo famiglia, consensi GDPR con validità e
canali, e una categoria. Servono per le ricevute e i tesseramenti federali (codice fiscale, indirizzo), per sapere a
chi scrivere quando il cliente è un minore e chi può venire a prenderlo, e per registrare i consensi firmati.

**Decisione.**

- **Anagrafica** sulla `persona` (unica per clienti, staff e tutori): sesso, luogo di nascita, codice fiscale,
  indirizzo, CAP, città, provincia, nazione, note. Il **codice fiscale** si normalizza (maiuscolo, senza spazi), si
  controlla anche il carattere di controllo (omocodie comprese) ed è unico: un doppione dà errore e la ricerca dei
  clienti lo trova. Solo ciò che serve: niente documenti d'identità, professione o dati sanitari nelle note.
- **Tutori** come legame tra due persone (`legame`): relazione (madre, padre, tutore, nonno, altro), «riceve le
  comunicazioni», «può venire a prenderlo» (delegato al ritiro), dal/al. Il tutore è una persona come le altre: si
  sceglie dalla ricerca o si crea; se il codice fiscale c'è già, si collega quella persona. Togliere un tutore chiude il
  legame (si cancella se aggiunto per errore lo stesso giorno).
- **Nucleo famiglia ricavato**, non salvato: fratelli = chi ha un tutore in comune; nella scheda del tutore si vede di
  chi è tutore. Niente tabella «famiglia» da tenere allineata.
- **Promemoria ai tutori**: l'email va ai tutori che ricevono le comunicazioni e hanno un indirizzo; se non ce ne sono,
  alla persona. La pagina Scadenze e la scheda mostrano a chi andrà.
- **Uscita**: quando il tornello nega l'uscita a un minore senza permesso, il messaggio dice con chi può uscire
  (i tutori delegati al ritiro).
- **Consensi** (`consenso`): trattamento dei dati per il servizio, comunicazioni promozionali (con i canali: email,
  SMS, posta; almeno uno), foto e video. Chi ha firmato (per i minori un genitore), quando, fino a quando (facoltativo),
  revoca con la data. Lo storico resta.
- **Categoria**: non si aggiunge. Staff o cliente si ricava già (account e ruoli, abbonamenti); le categorie del
  vecchio gestionale si rivedranno con l'importazione dei dati.

**Conseguenze.**

- Le schede dei minori importate dal vecchio gestionale dovranno avere almeno un tutore con email per i promemoria.
- I consensi oggi si registrano (dal modulo cartaceo); non bloccano niente. In futuro: niente comunicazioni
  promozionali senza consenso, avviso se manca il trattamento, firma sul tablet.
- I dati del tutore (contatti) sono nella sua scheda: se cambia l'email della madre, vale per tutti i figli.
