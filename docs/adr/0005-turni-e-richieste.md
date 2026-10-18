# 0005 · Turni scoperti, nessuna sovrapposizione, percorso delle richieste

**Contesto.** Oggi i turni nascono in un Excel e i cambi si concordano su WhatsApp: nessuno controlla che una
persona non finisca in due posti alla stessa ora, e le sostituzioni si perdono nelle chat.

**Decisione.**
- Un turno può essere **scoperto** (senza persona): il coordinatore genera il mese dalla settimana tipo e
  assegna ciò che manca.
- **Nessuna sovrapposizione** per la stessa persona, garantita dal database (vincolo di esclusione su
  `tsrange(data + inizio, data + fine)`, estensione `btree_gist`) oltre che dal controllo nel servizio.
- **Generazione del mese** rilanciabile: un turno già presente (stessa postazione, giorno e inizio) non si
  duplica; se la persona del modello è già occupata il turno nasce scoperto con un avviso.
- **Richieste** con un solo percorso: chi chiede → (collega accetta, per sostituzioni e scambi) →
  coordinatore approva. Una sostituzione senza collega indicato è «aperta»: la può prendere chiunque dello
  staff dell'impianto. Gli effetti sui turni si applicano **solo all'approvazione**, in una transazione, dopo
  aver ricontrollato che i turni non siano cambiati e che nessuno finisca sovrapposto.
- Al massimo una richiesta aperta per turno (indice unico parziale).
- **Compensi** dalla tariffa per mansione valida nel giorno del turno; un'eccezione per persona vince.

**Conseguenze.** Il coordinatore approva tutto ciò che cambia i turni: resta il punto di controllo, come
oggi, ma senza ricopiare a mano. Uno scambio tra turni dello stesso giorno passa da uno stato intermedio
senza persona per rispettare il vincolo riga per riga.
