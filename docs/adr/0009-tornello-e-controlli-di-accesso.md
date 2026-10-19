# 0009 · Tornello: braccialetti, controlli di accesso modulari, tesseramento e abbonamenti

**Contesto.** Al tornello si passa il braccialetto NFC: verde o rosso. Oggi, quando è rosso, il tornellista deve
indagare. I casi noti (19/10/2026): certificato medico scaduto, fuori dalla fascia oraria del corso (si entra da 10
minuti prima dell'inizio), nessun abbonamento, abbonamento scaduto, tesseramento annuale (35 €, stagione
settembre–luglio) non pagato, braccialetto bannato, braccialetto smarrito. Altri casi arriveranno. Esiste il
«golden pass»: i braccialetti di quella persona danno sempre verde (dipendenti, tornellisti). Una persona può avere più
braccialetti: se ne perde uno resta suo, nella speranza che si ritrovi, e intanto ne riceve un altro. I bambini si
scambiano spesso i braccialetti per sbaglio.

**Termini.** *Tesseramento*: la quota annuale per stagione. *Abbonamento*: il pagamento di un corso (mono, bi o
trisettimanale) oppure un *carnet* di ingressi. *Iscrizione* resta solo l'iscrizione a un turno del corso (ADR 0008).

**Decisione.**

- **Controlli indipendenti.** Ogni caso è una classe `ControlloAccesso` con codice, ordine (prima il braccialetto,
  poi la persona, poi il momento), parametri e un metodo che, dato il contesto del passaggio, restituisce zero o più motivi
  (codice, messaggio, cosa fare). Un caso nuovo è una classe nuova: il resto non cambia.
- **Regole configurabili.** Tabella `regola_accesso`: per ogni controllo, attivo sì/no e parametri (es. minuti prima
  dell'inizio, chiusura a fine corso o N minuti dopo l'inizio, minuti di rientro del carnet), modificabili dagli admin.
- **Tutti i motivi insieme.** Si valutano tutti i controlli: il tornello mostra il quadro completo, non solo il primo
  problema. Insieme all'esito si mostra sempre **nome, cognome ed età** della persona legata al braccialetto, così uno
  scambio tra bambini si vede subito.
- **Braccialetti.** Stato *attivo*, *smarrito* o *disattivato* (restituito, rotto); più braccialetti per persona; lo
  stesso codice non può essere in uso da due persone (un braccialetto disattivato si può riassegnare). Codice
  normalizzato in maiuscolo senza separatori (ADR 0004).
- **Ban** sul singolo braccialetto oppure sulla persona (vale per tutti i suoi braccialetti), con motivo e periodo.
- **Golden pass** sulla persona, con periodo: salta certificato, tesseramento, abbonamento e fascia oraria. Restano
  il controllo del braccialetto e il ban: un braccialetto smarrito di un dipendente non deve aprire il tornello a chi lo
  trova, e un ban vale più del golden pass. Ogni controllo dichiara se vale anche con il golden pass.
- **Fascia oraria**: dai turni a cui la persona è iscritta (ADR 0008) che si tengono quel giorno, compresi quelli
  spostati; di base da 10 minuti prima dell'inizio fino alla fine.
- **Carnet**: scala un ingresso al primo passaggio; nei 90 minuti successivi (parametro) i passaggi non scalano, così
  chi esce e rientra non perde ingressi. Chi ha un carnet utilizzabile non ha il vincolo della fascia oraria. Chi ha un
  abbonamento al corso ed è nella fascia del suo turno non consuma il carnet.
- **Certificato**: valido fino al giorno di scadenza compreso, poi blocca. Nessuna tolleranza.
- Ogni passaggio si registra (verde o rosso, motivi, carnet scalato, chi ha letto). Ogni apertura della scheda cliente
  si registra (ADR 0003).
- Moduli: `clienti` (certificati, tesseramenti, abbonamenti, scheda) e `accessi` (braccialetti, ban, golden pass,
  regole, controlli, passaggi). `accessi` usa le API pubbliche di `clienti`, `pianovasca` e `identita`.

**Conseguenze.**

- Il giallo («passa ma avvisa», es. certificato in scadenza) è rimandato: dipende da cosa può mostrare il tornello
  fisico. Un motivo ha già il campo «blocca»: un controllo potrà restituire avvisi che non bloccano.
- Un codice sconosciuto è sempre rosso, qualunque regola sia attiva.
- I parametri sono per tutta la società; quando arriveranno altri impianti le regole potranno avere un ambito.
- L'ingresso forzato non serve: lo copre il golden pass.
