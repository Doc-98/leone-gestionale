# Leone Gestionale

Gestionale per la società sportiva Leone XIII: si parte dalla piscina (turni dello staff, piano vasca,
clienti e accessi ai tornelli), con una struttura pronta per altri sport.

Architettura e decisioni: [`docs/architettura.md`](docs/architettura.md) · [`docs/adr/`](docs/adr/).

## Stato

**Fase 1 – turni dello staff.**

- *Staff*: i propri turni per settimana; da un turno si chiede un cambio orario, una sostituzione (a un collega
  o aperta a tutti) o uno scambio; «Turni da coprire» per offrirsi; ore e compenso del mese.
- *Coordinatore*: pianificazione della settimana con i turni scoperti in evidenza, creazione e modifica dei
  turni, generazione del mese dalla settimana tipo, approvazione delle richieste, ore e compensi di tutto lo
  staff (anche in CSV).
- Garanzie nel database: nessun turno sovrapposto per la stessa persona, una sola richiesta aperta per turno.

## Struttura

```
backend/    Java 21 + Spring Boot 4 · un pacchetto per modulo (identita, struttura, staff, …)
frontend/   Svelte 5 + TypeScript + Vite · design system «Corsia» (da ClockWork)
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
| GET · POST | `/api/modelli` · `/api/modelli/{id}/genera` `{mese}` · `/api/modelli/{id}/righe` | coordinatori |
| POST | `/api/richieste` | sui propri turni: `CAMBIO_ORARIO`, `SOSTITUZIONE`, `SCAMBIO` |
| GET | `/api/richieste/mie` · `/api/richieste/aperte` | le proprie / le sostituzioni aperte |
| POST | `/api/richieste/{id}/accetta` · `/rifiuta` · `/annulla` | collega / richiedente |
| GET · POST | `/api/richieste` · `/{id}/approva` · `/{id}/respingi` | coordinatori |
| GET | `/api/riepilogo/mio?mese=` | ore e compenso propri |
| GET | `/api/riepilogo?mese=` · `/api/riepilogo/csv?mese=` | coordinatori |

Le richieste che modificano dati devono rimandare il token CSRF nell'intestazione `X-XSRF-TOKEN`.
Gli errori seguono il formato «problem details» (RFC 9457): 400 dati non validi, 403 permessi,
404 non trovato, 409 conflitto (es. turni sovrapposti).
