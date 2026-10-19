# 0008 · Fasce definite, iscrizioni ai turni dei corsi, presenze

**Contesto.** In piscina la giornata è divisa in fasce (es. 17:00–17:45, 17:50–18:35): in ogni fascia quasi ogni corsia
ha un corso diverso e la vasca grande è divisa in 5 o 6 corsie a seconda di come si mettono i divisori. La
segreteria deve vedere a colpo d'occhio quanti iscritti ha un corso in un certo giorno e orario; gli istruttori
hanno bisogno della lista per segnare le presenze, soprattutto con i bambini. Il dato di base è il **trittico corso
+ giorno + orario** («turno del corso»): un corso si tiene in più giorni e in più fasce; una persona si iscrive a uno
o più turni settimanali dello stesso corso.

**Decisione.**

- Il turno del corso è la riga di `lezione` (corso, giorno della settimana, orario, spazi, periodo). Ha i **posti**
  (facoltativi).
- **Fasce definite** (`fascia_oraria`): per vasca e giorno della settimana, con orari liberi scelti dal
  coordinatore e un **interruttore 5/6 corsie** per fascia. Il database impedisce fasce che si accavallano nella
  stessa vasca e giorno. Un turno di corso in una fascia deve usare corsie del suo assetto; cambiando l'assetto i
  corsi si riportano sulle corsie con lo stesso numero (se una corsia non esiste più, il cambio si blocca e dice
  quali corsi spostare). Cambiando l'orario di una fascia, i turni che avevano lo stesso orario la seguono. Dove
  non ci sono fasce definite, il piano le ricava dalle lezioni (ADR 0007).
- **Iscrizione** = persona ↔ turno del corso, da una data e fino a un'altra (ritiro, cambio di turno). Niente
  doppioni nello stesso periodo (vincolo nel database). Oltre i posti si iscrive solo confermando il soprannumero.
  Dalla segreteria si può creare una persona minima (nome, cognome, data di nascita): la scheda cliente completa
  arriva con la fase 3.
- **Presenza** = turno + data + persona, presente o assente; si segna il giorno della lezione o dopo, solo nelle
  date in cui il turno si tiene (non saltato né spostato altrove; sì se spostato in quella data).
- **Chi vede cosa** (dati di minori): il numero di iscritti è visibile a tutti nel piano; la lista (nome ed età,
  nessun contatto) solo all'istruttore del turno, ai coordinatori e alla segreteria; iscrivono segreteria e
  coordinatori; le presenze le segnano l'istruttore del turno e i coordinatori.
- Iscrizioni e presenze stanno nel modulo `pianovasca` (sono «chi frequenta il piano»); la fase 3 (clienti, quote,
  certificati) le userà tramite le sue classi pubbliche, senza dipendenze circolari.

**Conseguenze.**

- Il piano mostra «7/8 iscritti» su ogni corso, in pianta e nella settimana; un clic apre la lista.
- Le presenze sono la base per i recuperi, le statistiche di frequenza e il controllo al tornello (fase 3).
- Il registro delle consultazioni delle schede (ADR 0003) andrà esteso anche alla lista degli iscritti.
