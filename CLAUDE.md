# Leone Gestionale – istruzioni per le sessioni di lavoro

- Si lavora su più sessioni in parallelo: **prima di ogni lavoro riallineati con `main`** (`git pull --ff-only`).
- Lingua: codice di dominio, commenti, messaggi e documentazione in **italiano** (nomi come `Turno`, `persona_id`).
  Termini tecnici standard (controller, repository, DTO) restano in inglese.
- Architettura e decisioni: `docs/architettura.md` e `docs/adr/`. Una decisione nuova e importante → un nuovo ADR.
- Backend: monolite modulare, un pacchetto per modulo sotto `it.leonexiii.gestionale`. Un modulo non legge le
  tabelle di un altro: usa le classi pubbliche dell'altro modulo (es. `identita.Persone`) o gli id.
- Lo schema si modifica **solo** con nuove migrazioni Flyway (`V<n>__descrizione.sql`), mai cambiando quelle già
  pubblicate. Dati demo in `db/demo` (profilo `demo`), solo persone inventate.
- Dati personali di minori: niente dati reali nel repository, nei test o negli screenshot.
- Orari «da calendario» come data + ora locale, fuso Europe/Rome.
- Prima di pubblicare: `mvn verify` nel backend, `npm run check && npm test && npm run build` nel frontend.
  Se si tocca il programma per Windows: `cargo build` in `desktop/src-tauri` (l'installer lo fa la CI su Windows).
- Aprire una pull request solo quando l'utente lo chiede.
