# 0011 · Sotto controllo, cooldown, uscita autonoma, certificato provvisorio, sospensioni

**Contesto.** Dal confronto con il gestionale attuale (TeamSystem Wellness, `docs/gestionale-attuale.md`) e dalle
risposte del 20/10/2026:

- l'«alert list»: la persona entra, ma la segreteria viene avvisata a ogni suo accesso;
- un «tempo di disabilitazione» dopo un passaggio, oggi non usato, utile contro il braccialetto passato a un amico,
  con l'eccezione di chi accompagna;
- oggi il tornello non controlla l'uscita, ma c'è un secondo lettore sul lato d'uscita; alcuni minorenni hanno un
  permesso scritto dei genitori per uscire da soli, oggi controllato a mano mostrando una copia firmata;
- il «certificato provvisorio»: una dichiarazione firmata di breve durata, in attesa del certificato;
- la sospensione degli abbonamenti (es. infortunio).

**Decisione.**

- **Sotto controllo** è un tipo di blocco che non blocca (`blocco.tipo = SORVEGLIANZA`, sulla persona o sul
  braccialetto). Il controllo «Sotto controllo» aggiunge un avviso all'esito (verde, con bordo e avviso ben visibili),
  il passaggio è *segnalato* e la segreteria riceve un avviso sullo schermo (controllo ogni 15 secondi).
- **Cooldown** (regola `RIPASSAGGIO`, spenta di base, 5 minuti): rosso se lo stesso braccialetto è entrato da meno di
  N minuti. Chi ha il permesso di *accompagnatore* può ripassare.
- **Lato del tornello.** Ogni passaggio ha un verso (entrata o uscita); la postazione sceglie il lato e lo ricorda.
  All'uscita valgono solo i controlli dichiarati per l'uscita: braccialetto (smarrito o disattivato), sotto
  controllo e la regola `USCITA` (spenta di base): un minorenne (età configurabile) esce da solo solo con il permesso
  di *uscita autonoma* registrato, con chi l'ha firmato e quando. All'uscita non contano certificato, abbonamento,
  fascia e non si scala il carnet.
- **Permessi firmati** (`autorizzazione`): uscita autonoma, accompagnatore; con periodo e revoca.
- **Certificato provvisorio**: tipo di certificato con chi ha firmato la dichiarazione e la data; vale al massimo
  N giorni dalla firma (parametro della regola, 30 di base) e fa passare con un avviso.
- **Sospensione**: nei giorni sospesi l'abbonamento non vale (rosso «sospeso», con il motivo) e la scadenza si allunga
  di altrettanti giorni; le sospensioni non si accavallano e si annullano se inserite per errore.

**Sul certificato provvisorio (da verificare con chi segue la società).** Per legge il certificato medico non
agonistico serve ai tesserati delle federazioni (FIN) e degli enti del CONI e nelle attività parascolastiche; per le
attività ludico-motorie e amatoriali (es. nuoto libero) non è obbligatorio e lo chiede la struttura. Una dichiarazione
firmata non sostituisce il certificato dove la legge lo richiede, e in generale un «esonero di responsabilità» non
copre i danni causati da colpa grave. Il gestionale permette il provvisorio e ne limita la durata; quando e per chi
usarlo lo decide la società.

**Conseguenze.** Le regole nuove sono spente o non bloccanti finché la società non le accende. Le uscite si possono
già registrare: il giorno in cui si accende il lettore d'uscita non serve altro.
