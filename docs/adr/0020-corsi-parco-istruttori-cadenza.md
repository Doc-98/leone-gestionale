# 0020 · Corsi: parco istruttori, istruttori per turno, turni mensili

**Stato:** accettata

## Contesto

Un corso non ha sempre lo stesso istruttore: i Delfini Azzurri, per esempio, sono seguiti da più istruttori che si
alternano o lavorano in coppia (al sabato). Finora corso e turno avevano un solo `istruttore_id`. Inoltre dal nuovo
menu rapido della scheda corso un turno si crea «una volta», «ogni settimana», «ogni mese» o «nei giorni scelti».

## Decisione

- **Parco istruttori del corso** (`corso_istruttore`): chi lavora per quel corso. Il primo resta in `corso.istruttore_id`
  come istruttore di riferimento, per i turni che non ne hanno uno proprio e per compatibilità.
- **Istruttori del turno** (`lezione_istruttore`): uno o più, di solito dal parco; chi viene assegnato a un turno senza
  essere nel parco ci entra. Il primo resta in `lezione.istruttore_id`. Un turno senza istruttori vale come tenuto
  dall'istruttore del corso; il registro presenze lo apre chiunque tenga il turno.
- **Cadenza** del turno (`lezione.cadenza`): `SETTIMANALE` (come prima) oppure `MENSILE`, cioè lo stesso giorno della
  settimana nella stessa posizione del mese della prima data (il «quarto mercoledì»): la prima data deve cadere in quel
  giorno. «Una volta» è un turno con `valida_dal = valida_al`; «giorni scelti» sono più turni settimanali, uno per giorno.
- L'API accetta ancora `istruttoreId` (un solo istruttore) quando `istruttori` manca. `GET /api/corsi` restituisce per
  ogni turno anche gli iscritti di oggi e quante persone sono in lista d'attesa, per l'elenco e la scheda del corso.

## Conseguenze

- Migrazione V18 (le tabelle nuove si riempiono dai campi singoli). I dati demo nascono dopo la V18, quindi la V1019
  ripete il passaggio e aggiunge un secondo istruttore ai corsi di scuola nuoto.
- Il piano vasca e le presenze leggono la cadenza tramite `Lezione.cadeIl`; l'occorrenza del piano mostra ancora un solo
  istruttore (il primo).
