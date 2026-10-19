# 0007 · Piano vasca: lezioni ricorrenti calcolate, eccezioni, sovrapposizioni come avvisi

**Contesto.** Il piano vasca mostra per giorno e per settimana corsi, lezioni private e di prova, gare e chiusure,
su vasche e corsie. I corsi si ripetono ogni settimana per mesi; ogni tanto una lezione salta o si sposta; le
chiusure (feste, manutenzione) sospendono tutto. Lo staff lo consulta, i coordinatori lo modificano.

**Decisione.**

- **Le lezioni si salvano come regola, non come elenco di date**: corso → lezione (giorno della settimana,
  orario, periodo di validità, spazi). Le occorrenze si calcolano al momento per il periodo richiesto
  (`GET /api/piano`, al massimo due mesi). Cambiare l'orario di un corso è una sola modifica.
- **Eccezioni per data**: una lezione in un giorno può essere *saltata* o *spostata* (altro giorno/orario), con un
  motivo. Annullare l'eccezione la riporta regolare.
- **Eventi puntuali** (gara, lezione privata, lezione di prova, chiusura, altro), anche su più giorni. Una
  **chiusura** senza spazi vale per tutto l'impianto; le lezioni che ci cadono risultano «sospese» senza
  toccarle.
- **Spazi gerarchici**: le corsie sono spazi con la vasca come padre. Una vasca intera tocca tutte le sue corsie.
- **Assetti delle corsie**: con i divisori la vasca grande si usa a 6 oppure a 5 corsie. Ogni corsia appartiene a
  un assetto (`spazio.assetto` = numero di corsie). Corsie di assetti diversi della stessa vasca, nello stesso
  orario, si sovrappongono; una lezione usa corsie di un solo assetto.
- **Il piano si legge per fasce** (aggiornato il 19/10/2026 su indicazione di chi lavora in vasca): in ogni fascia
  da 45 minuti quasi ogni corsia ha un corso diverso, quindi una griglia oraria non basta. Le fasce si calcolano
  dalle lezioni: orari che si sovrappongono per almeno metà della lezione più corta stanno nella stessa fascia (la
  vasca piccola, sfalsata di 5 minuti, finisce con la grande); le attività oltre i 60 minuti (nuoto libero,
  Propaganda) compaiono in tutte le fasce che attraversano. Viste: giorno = elenco delle fasce + **pianta** delle
  vasche (una striscia per corsia con corso, istruttore, età; corsie libere); settimana = tabella fasce × giorni con
  i corsi di ogni fascia; la griglia oraria per corsia resta come alternativa sul PC.
- **Colore del corso** facoltativo (es. i livelli Delfino Giallo, Verde, Azzurro…), mostrato nella pianta.
- **Le sovrapposizioni sono avvisi, non errori**: due attività sugli stessi spazi (o vasca e sua corsia) nello
  stesso orario vengono segnalate nel piano e restituite come `avvisi` dopo ogni modifica, ma si possono salvare.
  Nella realtà capita di dividere una corsia o di decidere all'ultimo; il turno dello staff invece resta un
  vincolo del database (ADR 0005), perché una persona non può essere in due posti.
- Niente si cancella: una lezione già iniziata, tolta dal piano, smette da domani; un corso si disattiva; un
  evento si annulla.

**Conseguenze.**

- Nessuna tabella di «occorrenze» da tenere allineata; il calcolo è in memoria e veloce per un impianto.
- Il registro di chi c'era (presenze, ingressi) quando arriverà dovrà riferirsi a lezione + data, non a un id di
  occorrenza.
- Le lezioni private hanno per ora solo un titolo: il collegamento al cliente arriva con la fase 3 (e lì andrà
  deciso chi vede il nome di un minore nel piano).
