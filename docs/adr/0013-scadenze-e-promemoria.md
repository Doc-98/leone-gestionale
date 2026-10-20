# 0013 · Scadenze e promemoria via email

**Contesto.** Il tornello dà rosso quando scade il certificato o manca il tesseramento, e spesso la famiglia lo scopre
lì. Caso tipico: l'abbonamento si rinnova a giugno/luglio, il tesseramento annuale slitta a settembre, e bambini con
abbonamento e certificato in ordine restano senza tesseramento. Serve una pagina pratica per la segreteria e un
promemoria automatico (un mese e una settimana prima per il certificato, e così via), con i giorni impostabili.

**Decisione.**

- **Pagina Scadenze** (permesso «Clienti»): certificati, abbonamenti e carnet (quasi finiti), tesseramento in
  scadenza o **mancante** per chi ha l'abbonamento valido; solo per chi frequenta (abbonamento valido, in arrivo o
  finito da poco). Entro 7/15/30/60 giorni, ricerca, conteggio degli urgenti, elenco scaricabile, apertura della
  scheda, invio di un promemoria a mano, data dell'ultimo promemoria. Un abbonamento già rinnovato non compare.
- **Promemoria** come regole modificabili (pagina «Promemoria email»): tipo, giorni prima della scadenza (per il
  tesseramento mancante: ogni quanti giorni e al massimo quante volte per stagione), attivo, oggetto e testo con
  segnaposto ({nome}, {cognome}, {scadenza}, {giorni}, {corso}, {stagione}, {societa}) e anteprima. Di partenza:
  certificato a 30 e 7 giorni, abbonamento a 15, tesseramento mancante ogni 7 giorni (3 volte); tesseramento in
  scadenza spento.
- **Giro automatico** ogni mattina alle 9 (`gestionale.promemoria.cron`, «-» per spegnerlo), più «manda adesso».
  Ogni invio si registra (`invio_promemoria`): lo stesso promemoria non parte due volte; se più regole coprono la
  stessa scadenza nello stesso giorno parte una sola email.
- Modulo **notifiche**: invio via SMTP (`spring.mail.*`, mittente `gestionale.posta.mittente`) e **registro di ogni
  email** (inviata, non partita perché la posta non è configurata, senza indirizzo, errore), visibile nella scheda
  del cliente e nei promemoria. Senza SMTP tutto funziona e si vede, ma non parte niente.
- Contatti nella scheda (email, telefono). Per i minori oggi si scrive l'email di un genitore; con i tutori i
  promemoria andranno ai tutori.

**Conseguenze.**

- Le email di servizio presuppongono il consenso al trattamento per il servizio (nel vecchio gestionale c'è, con
  scadenza): da registrare con la scheda completa.
- Il server di posta va configurato sul server di produzione (credenziali fuori dal repository).
- In futuro: SMS, promemoria per i pagamenti, email della password temporanea.
