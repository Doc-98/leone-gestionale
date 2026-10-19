# Leone Gestionale

Gestionale per la società sportiva Leone XIII: si parte dalla piscina (turni dello staff, piano vasca,
clienti e accessi ai tornelli), con una struttura pronta per altri sport.

Architettura e decisioni: [`docs/architettura.md`](docs/architettura.md) · [`docs/adr/`](docs/adr/).

## Stato

**Fase 1 – turni dello staff (completa).** Una sola interfaccia in due forme: **programma per Windows** per gli
uffici (menu laterale, griglie ampie) e **app** sul telefono per lo staff (ADR 0006).

- *Staff*: i propri turni per settimana; da un turno si chiede un cambio orario, una sostituzione (a un collega
  o aperta a tutti) o uno scambio; «Turni da coprire» per offrirsi; ore e compenso del mese.
- *Coordinatore*: pianificazione della settimana a griglia (postazioni × giorni) con i turni scoperti in
  evidenza e le ore per persona; settimana tipo modificabile (anche copiandola per una nuova stagione) e
  generazione del mese; approvazione delle richieste; mansioni, postazioni e tariffe orarie; ore e compensi
  di tutto lo staff, in CSV e come **foglio ore nel modello Excel della società** (uno per persona o tutti in .zip).
- Garanzie nel database: nessun turno sovrapposto per la stessa persona, una sola richiesta aperta per turno.

**Fase 2 – piano vasca (completa).** Il giorno per fasce, con la pianta delle vasche (cosa c'è in ogni corsia, vasca
grande a 5 o 6 corsie, vasca piccola), la settimana come tabella fasce × giorni con i corsi di ogni fascia, la
griglia oraria come alternativa.
Corsi con lezioni settimanali, salti e spostamenti di una singola data, eventi (gare, lezioni private e di prova,
chiusure anche di più giorni) e sovrapposizioni segnalate. Lo staff lo consulta; coordinatori e admin lo modificano.

## Struttura

```
backend/    Java 21 + Spring Boot 4 · un pacchetto per modulo (identita, struttura, staff, …)
frontend/   Svelte 5 + TypeScript + Vite · design system «Corsia» (da ClockWork)
desktop/    programma per Windows (Tauri 2): una finestra sul server, con accesso all'hardware
docs/       architettura e decisioni (ADR)
infra/      docker-compose per il database di sviluppo
```

## Avviare in locale

Servono Java 21, Maven, Node 22 e Docker.

```bash
# 1. Database
docker compose -f infra/docker-compose.yml up -d

# 2. Backend con i dati demo (http://localhost:8080)
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=demo

# 3. Frontend (http://localhost:5173, le chiamate /api vanno al backend)
cd frontend && npm install && npm run dev
```

Account demo (password `demo1234`): `admin@demo.leone`, `coordinatore@demo.leone`, `staff@demo.leone`,
`giulia@demo.leone`, `marta@demo.leone`. Sono persone inventate: nello sviluppo non si usano dati reali.

## Programma per Windows

Il programma (`desktop/`) è una finestra sul server della società: al primo avvio chiede l'indirizzo del
server, poi apre l'interfaccia servita da lì (sessione e aggiornamenti come sul web). La parte nativa esporrà
lettore NFC e stampa; per ora dà versione e «Cambia server».

- **Installer**: a ogni modifica di `desktop/` la CI costruisce `.msi` e `.exe` su Windows (workflow
  «Programma Windows», scheda *Artifacts*); si può anche lanciare a mano.
- **In locale** (serve Rust): `cd desktop && npm ci && npx tauri build`. Per provarlo con il frontend di
  sviluppo, come server si indica `http://localhost:5173`.
- Il server deve offrire interfaccia e `/api` **sullo stesso indirizzo** (in sviluppo lo fa Vite; in
  produzione un proxy, da definire con il tecnico della società).

## Test

```bash
cd backend && mvn verify      # unitari + integrazione su Postgres in un container (serve Docker)
cd frontend && npm run check && npm test
```

Senza Docker, i test del backend possono usare un Postgres già avviato (database vuoto, ricreato a ogni giro):

```bash
TEST_DB_ESTERNO=true DB_URL=jdbc:postgresql://localhost:5432/gestionale_test DB_USER=… DB_PASSWORD=… mvn verify
```

Con il profilo `demo` la documentazione interattiva delle API è su http://localhost:8080/swagger-ui.html.

La CI (GitHub Actions) esegue entrambi a ogni push.

## API

| Metodo | Percorso | Chi |
|---|---|---|
| GET | `/api/auth/csrf` | tutti: imposta il cookie `XSRF-TOKEN` |
| POST | `/api/auth/login` · `/api/auth/logout` | `{email, password}` → utente e ruoli |
| GET | `/api/me` | collegati |
| GET | `/api/turni/miei?da=&a=` | i propri turni (default: prossimi 30 giorni) |
| GET | `/api/turni/staff?da=&a=` · `/api/staff/colleghi` | turni e colleghi degli impianti in cui si lavora |
| GET | `/api/turni?da=&a=` | coordinatori: tutti i turni, anche scoperti |
| POST · PUT | `/api/turni` · `/api/turni/{id}` · `/api/turni/{id}/annulla` | coordinatori |
| GET | `/api/staff/postazioni` · `/api/staff/persone` | coordinatori |
| GET · POST · PUT · DELETE | `/api/modelli` · `/{id}` · `/{id}/righe` · `/{id}/righe/{riga}` · `/{id}/genera` `{mese}` | coordinatori: settimana tipo |
| GET | `/api/staff/impianti` · `/api/staff/spazi` | coordinatori |
| GET · POST · PUT | `/api/staff/mansioni` · `/{id}` · `/{id}/postazioni` · `/api/staff/postazioni/{id}` · `/api/staff/postazioni/ordine` | coordinatori |
| GET · POST · PUT | `/api/staff/tariffe` · `/{id}` | coordinatori: una nuova tariffa chiude la precedente |
| POST | `/api/richieste` | sui propri turni: `CAMBIO_ORARIO`, `SOSTITUZIONE`, `SCAMBIO` |
| GET | `/api/richieste/mie` · `/api/richieste/aperte` | le proprie / le sostituzioni aperte |
| POST | `/api/richieste/{id}/accetta` · `/rifiuta` · `/annulla` | collega / richiedente |
| GET · POST | `/api/richieste` · `/{id}/approva` · `/{id}/respingi` | coordinatori |
| GET | `/api/riepilogo/mio?mese=` | ore e compenso propri |
| GET | `/api/riepilogo?mese=` · `/api/riepilogo/csv?mese=` | coordinatori |
| GET | `/api/piano?da=&a=` | tutti: spazi e occorrenze del periodo (max due mesi), con stato e sovrapposizioni |
| GET · POST · PUT | `/api/corsi` · `/{id}` · `/{id}/lezioni` | lettura per tutti, modifiche coordinatori |
| PUT · DELETE | `/api/lezioni/{id}` · POST `/api/lezioni/{id}/eccezioni` · DELETE `/api/eccezioni/{id}` | coordinatori: salti e spostamenti |
| GET · POST · PUT | `/api/eventi/{id}` · `/api/eventi` · `/{id}/annulla` | lettura per tutti, modifiche coordinatori |
| GET | `/api/foglio-ore/mio?mese=` | il proprio foglio ore (.xlsx) |
| GET | `/api/foglio-ore/persone/{id}?mese=` · `/api/foglio-ore/tutti?mese=` · `/api/foglio-ore/voci` | coordinatori (.xlsx, .zip, colonne del modello) |

Le richieste che modificano dati devono rimandare il token CSRF nell'intestazione `X-XSRF-TOKEN`.
Gli errori seguono il formato «problem details» (RFC 9457): 400 dati non validi, 403 permessi,
404 non trovato, 409 conflitto (es. turni sovrapposti).
