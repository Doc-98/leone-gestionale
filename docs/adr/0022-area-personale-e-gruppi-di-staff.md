# 0022 · Area personale: viste a scelta, «le mie ore» e «lo staff», gruppi di staff

**Stato:** accettata

## Contesto

Dal mockup «Area personale» (ottobre 2026) sono uscite tre letture dei turni (la settimana, il mese, la settimana a
colonne per il PC), due delle richieste (tutto in una pagina, a schede) e una pagina delle ore divisa in due: le proprie
ore, che vede chiunque lavori, e le ore dello staff, che vede solo chi ne ha il permesso. Finora la pagina delle ore
metteva le due cose insieme e il permesso «Ore dello staff» valeva per impianto: un coordinatore vedeva tutto lo staff
dell'impianto, anche i gruppi che non coordina. La direzione invece deve vedere tutto, per gruppo e in totale.

Gli assistenti sono collaboratori (co.co.co.): chiedere una sostituzione vuol dire avvisare che quel giorno non ci si sarà.

## Decisione

- **Viste a scelta**, ricordate per account (`lib/preferenze.ts`): `turni.vista` (settimana · mese · colonne; la vista a
  colonne solo su schermi larghi) e `richieste.vista` (pagina · schede), dall'ingranaggio in alto. Le ore hanno invece
  l'interruttore «Le mie ore | Lo staff», visibile solo con il permesso «Ore dello staff».
- **Gruppi di staff**: il gruppo è la **mansione** (assistenti spogliatoio, atrio, bagnanti…). Ogni mansione può avere
  dei **coordinatori** (tabella `mansione_coordinatore`, si scelgono in Impostazioni › Postazioni e mansioni). Con il
  permesso «Ore dello staff», chi coordina uno o più gruppi di un impianto vede ore, compensi, fogli ore e CSV solo di
  quei gruppi; chi ha il permesso ma non coordina nessun gruppo dell'impianto (la direzione) li vede tutti.
- **`GET /api/riepilogo`** restituisce anche i gruppi visibili (coordinatori, persone, ore, compensi, turni scoperti,
  tariffe mancanti) e, per ogni persona, il gruppo in cui ha fatto più ore; con `?gruppo=` (anche per il CSV e lo zip
  dei fogli ore) si limita a un gruppo, e un gruppo non visibile dà 403.
- **Sostituzioni**: il coordinatore conferma chi sostituisce (o propone il turno a qualcuno in particolare), ma
  l'assenza non si discute. Nell'area personale una sostituzione conclusa dice «coperta da …», mai «respinta».

## Conseguenze

- Senza coordinatori indicati non cambia niente: chi ha il permesso vede tutti i gruppi del suo impianto, come prima.
- Una persona che lavora in più gruppi compare in ognuno per le ore fatte lì; nell'elenco di tutto il personale sta sotto
  il gruppo in cui ha fatto più ore.
- Restano da fare con le pagine di gestione: proporre una sostituzione a un collega preciso dalla pagina «Da approvare»
  e togliere da lì il «respingi» per le sostituzioni.
