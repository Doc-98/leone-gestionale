# 0003 · Sessioni lato server e tre livelli di accesso ai dati dei clienti

**Contesto.** I clienti sono in gran parte minori. Lo staff usa telefoni propri; la società può dare un
dispositivo alla postazione dei tornelli. Serve poter revocare subito un accesso.

**Decisione.**
- Login con sessione lato server e cookie HttpOnly (non JWT): si revoca chiudendo la sessione. Protezione CSRF attiva.
- Ruoli con ambito (`ADMIN`, `COORDINATORE`, `SEGRETERIA`, `STAFF`, eventualmente limitati a un impianto).
- Dati dei clienti su tre livelli: (1) esito dopo la lettura di un bracciale, da qualsiasi telefono dello
  staff; (2) scheda essenziale da un dispositivo della società registrato, con PIN del dipendente;
  (3) tutto, per segreteria e admin.
- Registro di ogni consultazione di una scheda cliente.

**Conseguenze.** Serve una tabella dei dispositivi registrati e un modo per associarli (codice mostrato
dall'admin). Le API dei clienti controllano livello e dispositivo, non solo il ruolo.
