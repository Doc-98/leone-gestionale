# 0010 · Ruoli modificabili con permessi

**Contesto.** Finora i quattro ruoli (admin, coordinatore, segreteria, staff) erano fissi nel codice. La segreteria
deve poter fare quasi tutto (tranne le funzioni avanzate di sistema), e la società vorrà ritoccare i permessi o
creare ruoli nuovi (es. «istruttore senior» che segna le presenze di tutti) senza cambiare il codice.

**Decisione.**

- **Permessi fissi, ruoli come dati.** Un permesso corrisponde a controlli nel codice, quindi la lista è fissa:
  fa i turni, pianificazione, approvazioni, ore dello staff, mansioni e tariffe, modifica del piano, iscrizioni,
  presenze di tutti, clienti, braccialetti e ban, golden pass, regole del tornello, ruoli e permessi. I ruoli
  (tabella `ruolo`) e i loro permessi (`ruolo_permesso`) si cambiano dalla pagina «Ruoli e permessi».
- **Basi per tutti.** Chi ha un ruolo qualsiasi vede i propri turni, chiede cambi, vede il piano vasca, usa il
  tornello, vede lista e presenze dei turni in cui è istruttore. Non sono permessi.
- **Ruoli di sistema.** ADMIN, COORDINATORE, SEGRETERIA, STAFF non si eliminano. ADMIN ha sempre tutti i poteri
  e non si modifica. Di partenza coordinatore e segreteria hanno tutta la gestione tranne «ruoli e permessi» e
  «regole del tornello»; lo staff «fa i turni».
- **Ambito.** Un ruolo si assegna su un impianto o su tutta la società; un permesso vale dove vale il ruolo che lo
  dà (`Autorizzazioni`, nel modulo `identita`, che gli altri moduli usano). Nei controller:
  `@PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI')")`; nei servizi, il controllo per impianto.
- **Subito valido.** Ruoli e permessi non stanno nella sessione: si leggono dal database (con una cache svuotata a
  ogni modifica), quindi un cambio vale alla richiesta successiva, senza rifare il login.
- **Prudenze.** Solo un admin nomina o toglie un admin; nessuno toglie a se stesso il ruolo di admin; chi non è
  admin non può dare a un ruolo permessi che non ha. Un ruolo assegnato almeno una volta resta nello storico e non si
  elimina (gli si tolgono i permessi).
- «Fa i turni» è una caratteristica, non un potere: ADMIN non la riceve in automatico (gli admin non compaiono tra
  lo staff da mettere in turno).

**Conseguenze.**

- I livelli di accesso ai dati dei clienti (ADR 0003) restano: il livello 2 (dispositivo registrato) arriverà come
  condizione in più sui permessi dei clienti.
- Un permesso nuovo richiede codice (e una migrazione per darlo ai ruoli che devono averlo).
