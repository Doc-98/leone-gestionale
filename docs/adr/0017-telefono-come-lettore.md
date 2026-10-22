# 0017 · Il telefono come lettore NFC del PC

**Contesto.** Al tornello e in segreteria ci sono lettori fissi; altrove (un PC senza lettore, il programma per
Windows in un ufficio, le prove in sviluppo) serve un modo semplice per leggere i braccialetti dal PC. Un telefono
Android legge l'NFC dal browser (ADR 0004), ma il PC no.

**Decisione.**

- **Abbinamento dal PC**: «Collega un telefono» (nel tornello e in «Assegna un braccialetto» della scheda) crea un
  abbinamento con un token casuale (192 bit) e un codice di 6 cifre. Il PC mostra un QR con l'indirizzo
  `…/#/lettore/<token>`; in alternativa sul telefono si apre `…/#/lettore` e si scrive il codice.
- **Il telefono non accede**: la pagina «Lettore NFC» legge i braccialetti con la Web NFC e manda al server **solo i
  codici**, autorizzata dal token. Non riceve dati dei clienti, quindi va bene anche un telefono personale. Si
  presenta ogni 20 secondi, così il PC mostra «Telefono collegato».
- **Il PC riceve** le letture chiedendole al server ogni 0,7 secondi (più di rado se la scheda non è in vista) e le
  passa alla pagina aperta: il tornello le controlla come un passaggio, la scheda le scrive nel campo del braccialetto.
  L'abbinamento resta cambiando pagina e ricaricando (memoria della scheda del browser).
- **In memoria, a tempo**: gli abbinamenti non vanno nel database. Finiscono con «Scollega», dopo 10 minuti senza il
  PC o comunque dopo 12 ore; al massimo 3 per account. Dopo 20 codici di 6 cifre sbagliati in un minuto si aspetta
  (contro chi prova a indovinarli).
- **In sviluppo** il PC usa `localhost`, che il telefono non raggiunge: il QR usa l'indirizzo del PC in rete (lo
  calcola Vite) e il frontend va avviato con `npm run dev:telefono`.

**Conseguenze.**

- Il telefono-lettore deve restare con la pagina aperta e lo schermo acceso (limite della Web NFC).
- Con più copie del server (oggi non previste) gli abbinamenti andrebbero spostati in un archivio condiviso.
- In futuro lo stesso canale può servire ai lettori fissi collegati in rete.
