# 0021 · Il banco del tornello: la giornata, chi è da sistemare, viste a scelta

**Stato:** accettata

## Contesto

Al banco la segreteria vuole vedere tutto in una pagina: l'esito di chi passa, il riepilogo del cliente, chi è rimasto
fuori e va sistemato, come sta andando la giornata. Finora il tornello mostrava solo l'esito e gli ultimi passaggi.
Dal mockup «Banco» (varianti A del tornello e dei clienti) sono uscite quattro letture utili dei passaggi e quattro dei
«da sistemare»: nessuna è migliore per tutti.

## Decisione

- **`GET /api/accessi/giornata`** (chiunque abbia un ruolo, come il tornello) restituisce la giornata in una volta:
  conteggi (persone entrate, con il carnet, dentro ora, entrate, uscite, rossi), la linea dei passaggi (per i rossi anche
  chi e perché), gli ultimi passaggi con i **turni di oggi** di chi passa e gli **arrivati** per turno, i «da sistemare»
  e i «sistemati».
- **Da sistemare** è chi oggi ha preso un rosso in entrata e da allora non è più passato in verde (una persona o, se
  nessuno lo porta, un braccialetto); diventa **sistemato** appena ripassa in verde. Non serve nessuna tabella nuova:
  si ricava dai passaggi. Chi ha sistemato (il pagamento, il certificato) non si dice, perché sta in altri moduli.
- **Dentro ora** = persone il cui ultimo passaggio verde di oggi è un'entrata; se nessuno è passato dall'uscita vale
  `null` (non si sa) e la pagina lo dice.
- I turni di oggi vengono da `pianovasca.Frequenze.alBanco` (molte persone insieme: categoria, colore del livello,
  iscritti di quel giorno), come prevede la regola dei moduli.
- **`GET /api/accessi/braccialetti?codice=`** (CLIENTI o BRACCIALETTI) dice di chi è un braccialetto: nella ricerca dei
  clienti il lettore apre la scheda di chi lo porta.
- Le viste dei passaggi (tessere, tabella, per corso, linea del tempo) e dei «da sistemare» (schede, righe, pillole,
  per motivo) si scelgono dalla pagina e si ricordano per account sul dispositivo (`lib/preferenze.ts`), come nel piano
  vasca e nei corsi. I clienti aperti di recente stanno nello stesso posto (`lib/recenti.ts`).

## Conseguenze

- La pagina si aggiorna da sola ogni 30 secondi e dopo ogni lettura; i passaggi caricati sono gli ultimi 60 (200 a
  richiesta). Per giornate molto piene la linea legge tutti i passaggi del giorno: va bene per i volumi di una piscina.
- Le azioni dei «da sistemare» portano alla scheda del cliente, dove si risolve; «fallo passare lo stesso» non c'è.
