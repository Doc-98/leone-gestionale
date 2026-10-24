# Il gestionale attuale (TeamSystem Wellness)

Appunti dalle schermate della scheda cliente (19/10/2026), senza dati personali. La società è una **S.S.D. a r.l.**
Il programma è TeamSystem Wellness per Windows, in una versione datata. Serve da mappa: cosa si salva e come
lavora la segreteria, non da modello grafico.

## Scheda cliente

Schede: Anagrafica · Tessera · Certificati · Abbonamenti · Prenotazioni · Borsellino · CRM · Ricerca.

**Anagrafica**
- Categoria (es. CLIENTE), codice cliente e id utente, titolo, nome, cognome, sesso, data e luogo di nascita (con
  età calcolata), indirizzo completo, nazione, due telefoni, professione, documento, codice fiscale / partita IVA.
- **Nucleo famiglia** (collega le persone di una famiglia).
- **Quota stagionale**: prima iscrizione, data di scadenza, importo → da noi il *tesseramento*.
- Altro: livello, due campi personalizzati, «Classe Leoniani»; venditore, contatto, istruttore, fonte.
- Mailing list (SMS ed email), note esterne, note interne, casella messaggi.
- **Blocca accesso / Blocca uscita** (il tornello controlla anche l'uscita).
- **Sospensione** (sospeso dal / al).
- **Consensi GDPR** con validità (per il servizio, per il marketing) e canali autorizzati (SMS, email, cartaceo).
- Foto della persona.

**Tessera** (il braccialetto)
- Codice in **decimale a 10 cifre**: è il seriale del chip letto dal telefono come esadecimale
  (es. `3a:9f:12:c4` → `0983503556`). Il nuovo gestionale accetta entrambi i formati (ADR 0004).
- Stato (es. ATTIVA), data dello stato, operatore dell'ultima modifica.
- **Gold card**: accesso sempre consentito (di solito ai gestori) → da noi il *golden pass*, ma sulla persona.
- **Alert list**: la persona entra, ma la segreteria viene avvisata a ogni suo accesso (persona «sotto controllo»).
- **Black list**: accesso disabilitato anche se in regola → da noi il *ban*.
- La tessera si paga (importo, «per cassa» o «per borsellino», metodo di pagamento, venditore).
- «Controllo accessi – gestione accompagnatore: consenti timbratura ripetuta durante il tempo di disabilitazione»:
  dopo un passaggio la tessera resta disabilitata per un certo tempo (anti-passback), tranne per chi accompagna.
- Stampa di una tessera cartacea con codice a barre.

**Certificati**
- Tipo (da un elenco), emesso il, scade il, **provvisorio**.
- Sotto, schede sanitarie (anamnesi, cartella clinica, posturale, cardiologia): sono dati sanitari; il nuovo
  gestionale non li tratta (serve solo sapere se il certificato è valido e fino a quando).

**Borsellino**: un credito prepagato con cui si pagano tessera e servizi → da vedere con i pagamenti.

## Cosa già copre il nuovo gestionale

Anagrafica completa (codice fiscale, indirizzo), tutori e nucleo famiglia, consensi GDPR con validità e canali, tesseramento, certificato con tipo e scadenza, abbonamenti e carnet, braccialetti (più di uno per
persona, smarrito, disattivato), golden pass, ban, controlli al tornello con tutti i motivi, registro dei passaggi.

## Decisioni (20/10/2026, ADR 0011)

- Alert list → **sotto controllo**: verde con avviso al tornello e avviso in segreteria.
- Tempo di disabilitazione → **cooldown** dopo un passaggio, spento di base, con il permesso di **accompagnatore**.
- Uscita: oggi non si controlla; previsto il **lato d'uscita** (c'è un secondo lettore) con il permesso di **uscita
  autonoma** firmato dai genitori per i minorenni.
- Certificato provvisorio → dichiarazione firmata di breve durata (al massimo N giorni), con avviso.
- Sospensione → l'abbonamento non vale in quei giorni e la scadenza si allunga.
- Foto: oggi non ci sono foto di bambini nel gestionale; resta un'idea per il futuro.

## Pagamenti e regole (dal sito e dalla segreteria, 23/10/2026)

Riferimento per i **dati demo** e per capire cosa deve saper fare il gestionale; tutto diventa impostazione (vedi
`docs/architettura.md`, «Per qualsiasi società sportiva», e la proposta in ADR 0018).

**Dal regolamento sul sito** (leonexiiisport.it): quota associativa annuale 35 € (1/9–31/8, una sola anche con più
attività, mai rimborsabile); corsi a prezzo per frequenza (es. mono 520 €, bi 795 €, tri 950 €), con prezzi diversi per
gli alunni dell'Istituto; pacchetti a lezioni (Aquababy 15 lezioni); nuoto libero annuale per frequenza e pacchetti di
ingressi con validità e fascia oraria; ingresso extra 5 €; quota fissa di 50 € per le squadre agonistiche. Riduzione
per iscrizione a stagione iniziata (−10% novembre … −40% da febbraio). Rate: quota + 50% all'iscrizione, il resto prima
dell'inizio; chi non paga riceve avviso, sollecito, poi sospensione. Recesso con rimborso in percentuale secondo la data
(100% entro luglio … niente dopo il 31/12), accreditato nel borsellino. Sospensione per malattia: credito oltre 6
settimane, certificato entro 4. Cambio di attività: si calcola la differenza. Pagamento in segreteria (contanti sotto
i 1000 €, bancomat, carte, Satispay) o con bonifico.

**Braccialetto**: cauzione di 10 €, restituita alla riconsegna, persa se lo si smarrisce. I crediti per phon e docce
(0,20 € l'uno, ricarica minima 5 €) si rimborsano riconsegnando il braccialetto, non se è perso.

**Borsellino**: oltre ai crediti per phon e docce, raccoglie in euro i rimborsi per recesso, sospensione o cambio di
attività; valgono un anno e si possono trasferire ai familiari iscritti.

**Phon e docce**: in spogliatoio i phon accettano monete da 20 centesimi oppure il braccialetto, che scala i crediti;
le docce hanno lettori simili e la prima doccia della giornata è gratuita. Il braccialetto contiene solo il numero di
serie: probabilmente le macchine sono in rete e leggono e scalano i crediti dal gestionale sul server (da verificare
con il tecnico: marca, modello e come parlano con il gestionale attuale).

**Ricevute** (segreteria): il POS stampa la sua ricevuta; le ricevute della società le fa il gestionale e le stampa su
una stampante A4 collegata al PC. Sono numerate e la numerazione riparte a ogni stagione. Si può chiedere la fattura.
Le famiglie chiedono ricevute per rimborsi, welfare e detrazioni fiscali.

**Cassa**: chiusura a fine giornata, con un fondo cassa. Il pagamento dei bonifici probabilmente si segna a mano nel
gestionale.

**Precedenza nei rinnovi**: gli alunni dell'Istituto e chi frequentava nella stagione precedente hanno la precedenza
sui nuovi; le richieste si mettono in coda in ordine di arrivo.

## Domande ancora aperte (aggiornate al 24/10/2026)

Pagamenti (per confermare la parte 5, già costruita in modo flessibile):
- Pagamenti misti (parte contanti, parte carta) e acconti che non corrispondono a una rata: si fanno?
- Fratelli: un pagamento e una ricevuta unici per la famiglia, o uno per figlio?
- Bonifici: chi controlla l'estratto conto e ogni quanto; serve segnare il riferimento (CRO)?

Borsellino, braccialetto, phon e docce:
- Il credito per recesso vale un anno: da quando? Si può restituire in denaro?
- I crediti per phon e docce si ricaricano solo in segreteria o anche alle macchine?
- Phon e docce: marca, modello, tecnico, collegamento con il gestionale attuale.

Ricevute e fatture:
- Una ricevuta di esempio (dati oscurati): intestazione, dicitura per le detrazioni, codici fiscali di genitore e figlio.
- Formato della numerazione (es. «2026/27-0001»); una serie o più (ricevute, fatture).
- Marca da bollo da 2 € sopra 77,47 €?
- Chi emette oggi le fatture (gestionale, commercialista) e se elettroniche (SdI).

Cassa:
- Chi fa la chiusura; una cassa o più postazioni; fondo cassa fisso?
- Cosa serve all'amministrazione dalla chiusura (stampa, file, solo totali per metodo).

Rinnovi e importazione:
- Date del periodo di prelazione; preiscrizione online e Satispay «entro 7 giorni».
- Come esportare i dati da TeamSystem per l'importazione.
