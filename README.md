# Leone Gestionale

Gestionale per la società sportiva Leone XIII: si parte dalla piscina (turni dello staff, piano vasca,
clienti e accessi ai tornelli), con una struttura pronta per altri sport.

Architettura e decisioni: [`docs/architettura.md`](docs/architettura.md) · [`docs/adr/`](docs/adr/).

## Stato

**Fase 0 – fondamenta.** Login con ruoli, schema iniziale (persone, account, ruoli, struttura, turni),
dati demo, API dei turni, frontend con «I miei turni» e «Tutto lo staff» per i coordinatori.

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

La CI (GitHub Actions) esegue entrambi a ogni push.

## API (prime)

| Metodo | Percorso | Chi |
|---|---|---|
| GET | `/api/auth/csrf` | tutti: imposta il cookie `XSRF-TOKEN` |
| POST | `/api/auth/login` | tutti: `{email, password}` → utente e ruoli |
| POST | `/api/auth/logout` | collegati |
| GET | `/api/me` | collegati |
| GET | `/api/turni/miei?da=&a=` | collegati: i propri turni (default: prossimi 30 giorni) |
| GET | `/api/turni?da=&a=` | `ADMIN`, `COORDINATORE`: tutti i turni con chi li fa |

Le richieste che modificano dati devono rimandare il token CSRF nell'intestazione `X-XSRF-TOKEN`.
Gli errori seguono il formato «problem details» (RFC 9457).
