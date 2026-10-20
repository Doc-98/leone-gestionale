# 0015 · Lista d'attesa dei turni pieni e cambio di turno

**Contesto.** I turni dei corsi hanno i posti (ADR 0008): quando un turno è pieno, oggi si può solo iscrivere in
soprannumero. Le famiglie che aspettano un posto finiscono su fogli e messaggi; quando qualcuno si ritira o cambia
giorno, la segreteria deve ricordarsi chi era il primo e chiamarlo. Il cambio di turno (altro giorno o orario, o il
passaggio a un altro corso) oggi sono due operazioni a mano: ritiro e nuova iscrizione.

**Decisione.**

- **Lista d'attesa per turno** (`attesa`): persona, quando è stata aggiunta (l'ordine è quello di arrivo), nota
  (es. «va bene anche un altro giorno»), ultima volta che è stata avvisata, stato (in attesa, iscritta, rinuncia).
  Non si entra due volte nella stessa lista, né in quella di un turno a cui si è già iscritti. Una persona può essere
  in attesa per più turni.
- **Posti calcolati**: per ogni turno, iscritti oggi e il primo giorno in cui c'è un posto (oggi, oppure il giorno dopo
  la fine di un'iscrizione: un ritiro o un cambio già registrati). La pagina «Liste d'attesa» mette in cima i turni
  con un posto e evidenzia il primo della lista.
- **Avviso**: un'email modificabile prima dell'invio, ai tutori che ricevono le comunicazioni (altrimenti alla
  persona), registrata come le altre (ADR 0013). Si segna «avvisato il…»; la regola dei 3 giorni per rispondere è nel
  testo, non automatica.
- **Iscrizione dalla lista**: rispetta i posti (dal giorno in cui si libera il posto), salvo conferma del soprannumero;
  chi viene iscritto a un turno, da qualunque parte, esce dalla sua lista d'attesa.
- **Cambio di turno** in un'unica operazione: dalla data scelta si frequenta il nuovo turno (anche di un altro corso),
  nel vecchio l'ultimo giorno è il giorno prima (se l'iscrizione non era ancora iniziata, si sostituisce). La fine
  dell'iscrizione resta la stessa. Se il nuovo turno è pieno: lista d'attesa o soprannumero.
- **Chi**: permesso «Iscrizioni», per impianto. Dalla scheda del cliente si vedono i turni, le liste d'attesa in cui è
  e si iscrive o si cambia turno scegliendo tra i turni del corso con i posti.

**Conseguenze.**

- Cambiando corso, l'abbonamento (che è per corso) va aggiornato a parte: la scheda lo ricorda. In futuro il cambio
  potrà proporre anche il cambio dell'abbonamento, con i pagamenti.
- Niente avvisi automatici: la segreteria decide chi chiamare (fratelli, età, preferenze nelle note). Si potrà
  aggiungere un promemoria quando un posto resta libero da qualche giorno.
- Le richieste di cambio da parte delle famiglie (dall'area clienti) arriveranno con l'area per tutori e clienti.
