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
- Regole di dominio e principi che valgono ovunque: `docs/regole-dominio.md`.

## Orchestrazione con i subagenti

La sessione principale fa da **orchestratore**: capisce la richiesta, decide, parla con l'utente, divide il lavoro e lo
ricompone. Il lavoro esecutivo va ai subagenti in `.claude/agents/`, ciascuno con il suo modello.

| Subagente | Modello | Quando |
|---|---|---|
| `architetto` | opus | ADR, piani che toccano più moduli, controllo dei principi |
| `designer` | opus | Varianti di mockup sui canvas, dal design system |
| `backend` | sonnet | Migrazioni, servizi, controller, test d'integrazione → `mvn verify` |
| `frontend` | sonnet | Schermate e `src/lib` con Vitest → check, test, build; differenze dal mockup |
| `tester` | sonnet | Test dalla specifica, senza leggere l'implementazione |
| `qa-visuale` | sonnet | Foto Playwright per ruolo × computer, tablet, telefono × tema chiaro e scuro |
| `documentazione` | sonnet | Indice ADR, architettura, README demo, design system, scenari della demo |
| `revisore` | opus | Revisione di sola lettura del diff prima di pubblicare |
| `coerenza` | opus | Coerenza di aspetto e comportamento: giro completo o solo le schermate toccate |
| `ricerca-dominio` | sonnet | Pratiche del settore per pagamenti, cassa, ricevute e simili |

**Come si procede**

1. **Piccole correzioni** (un testo, un colore, un bug evidente): le fa direttamente la sessione principale, senza
   subagenti.
2. **Esplorazione e mockup**: restano nella sessione principale, che parla con l'utente. Il `designer` prepara le
   varianti; la sessione principale le presenta, raccoglie i commenti sul canvas e fa scegliere l'utente. Nessuna
   variante si sceglie senza di lui.
3. **Lavoro grande**: `architetto` (piano e ADR) → `backend`, `frontend` e `tester` in parallelo dallo stesso brief →
   `qa-visuale` → `documentazione` → `revisore` (e `coerenza` sulle schermate toccate) → la sessione principale
   sistema, ricompone, fa il commit e riferisce all'utente, con le differenze dal mockup.
4. **Aree amministrative** (soldi, cassa, ricevute): prima `ricerca-dominio`, poi `architetto`.

**Il brief di un subagente** deve stare in piedi da solo: il subagente non vede la conversazione. Contiene obiettivo,
decisioni dell'utente (con il link al mockup scelto), file e ADR da leggere, vincoli, cosa restituire e come verificarlo.

**Regole per tutti**

- **Solo la sessione principale fa commit e push**, dopo aver controllato lo stato di `main` (altre sessioni lavorano in
  parallelo). I subagenti scrivono file e riferiscono.
- `backend` e `frontend` lavorano in cartelle diverse e possono andare in parallelo; due subagenti non toccano mai gli
  stessi file nello stesso momento.
- Un subagente che trova una regola ambigua non indovina: lo scrive nel resoconto, e la sessione principale chiede
  all'utente.
