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
  (`0c:7a:5c:ae` → `0209345710`). Il nuovo gestionale accetta entrambi i formati (ADR 0004).
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

## Borsellino (per i pagamenti)

Credito prepagato sul profilo, caricato e pagato in segreteria. In piscina serve per **docce e phon**. Oggi un credito
vale 0,20 € (da rendere configurabile). Da capire con i pagamenti: come docce e phon scalano i crediti (leggono il
braccialetto?) e se il borsellino paga anche altro (es. la tessera da 10 €).

## Da decidere

- Pagamenti: tessera, borsellino, ricevute (in attesa delle informazioni dalla segreteria).
- Come esportare i dati da TeamSystem per l'importazione (e che categorie usa: per ora non c'è una categoria, ADR 0014).
