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
griglia oraria come alternativa. Fasce definite dai coordinatori con interruttore 5/6 corsie; iscrizioni ai turni dei
corsi con posti (conteggio «7/8» nel piano), lista degli iscritti con le presenze per l'istruttore, iscrizioni e
ritiri per la segreteria.
Corsi con lezioni settimanali, salti e spostamenti di una singola data, eventi (gare, lezioni private e di prova,
chiusure anche di più giorni) e sovrapposizioni segnalate. Lo staff lo consulta; coordinatori e admin lo modificano.

**Fase 3 – clienti e tornello (in corso).** Al tornello si legge il braccialetto (NFC dal telefono Android, lettore
da scrivania o codice scritto) e si vede subito **nome ed età** di chi lo porta e **tutti i motivi** del rosso, ognuno
con cosa fare: braccialetto sconosciuto, smarrito, disattivato o bannato, persona bannata, certificato medico mancante o
scaduto, tesseramento annuale mancante o scaduto, abbonamento mancante, scaduto o non iniziato, carnet esaurito, fuori
dalla fascia del proprio turno (ADR 0009). Ogni caso è un controllo indipendente, attivabile e con i suoi parametri
nella pagina «Regole del tornello». Golden pass per dipendenti e tornellisti. Carnet che scala al primo passaggio e non
nei 90 minuti di rientro, a entrata libera o legato agli orari di un corso (es. nuoto libero). Ruoli modificabili con i
loro permessi (ADR 0010). La segreteria ha la scheda cliente: braccialetti (più di uno per persona), ban, golden pass,
certificato, tesseramento, abbonamenti, turni frequentati, ultimi passaggi e come andrebbe adesso al tornello.

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

Account demo (password `demo1234`): `admin@demo.leone`, `coordinatore@demo.leone`, `segreteria@demo.leone`,
`staff@demo.leone` (Luca, istruttore di Delfino Giallo), `giulia@demo.leone`, `marta@demo.leone`. Sono persone inventate: nello sviluppo non si usano dati reali.

Braccialetti demo da provare al tornello (si scrivono nel campo): `D0000001` carnet (verde, scala), `D0000002` Delfino
Giallo bisettimanale (verde solo nella fascia), `D0000003` certificato scaduto e tesseramento mancante, `D0000004`
smarrito (`D0000014` è il suo nuovo), `D0000005` braccialetto bannato, `D0000006` persona bannata, `D0000007` carnet
esaurito, `D0000008` abbonamento scaduto, `D0000009` nessun abbonamento, `D0000104` Giulia con golden pass,
`D0000010` carnet negli orari del nuoto libero, `D0000012` 14 anni con certificato provvisorio e permesso di
uscita autonoma (prova il lato «Uscita»), `FFFFFFFF` sconosciuto. Sofia (`D0000001`) è «sotto controllo»: la segreteria
riceve un avviso. Anche il codice decimale del vecchio gestionale funziona (es. `3489660929` = `D0000001`).

Pagina «Scadenze» (certificati, abbonamenti, tesseramento mancante) e promemoria email automatici con testi
modificabili; senza server di posta le email si registrano ma non partono. Account dello staff con password temporanea
(ADR 0012, 0013).

Scheda cliente completa (ADR 0014): anagrafica con codice fiscale (anche nella ricerca), tutori con «riceve le
comunicazioni» e «può venire a prenderlo», fratelli, consensi privacy. Prova Tommaso Neri (`D0000002`): la madre
Paola riceve i promemoria e la sorella Sara compare tra i fratelli.

La segreteria ha di partenza tutta la gestione; ruoli e permessi si cambiano dalla pagina «Ruoli e permessi» (admin).

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
| GET · POST · PUT · DELETE | `/api/fasce` · `/{id}` · POST `/{id}/assetto` `{assetto}` | lettura per tutti, modifiche coordinatori |
| GET · POST | `/api/lezioni/{id}/iscritti?data=` | lista: istruttore del turno, coordinatori, segreteria; iscrive la segreteria |
| POST · PUT | `/api/iscrizioni/{id}/termina` · `/api/lezioni/{id}/presenze` `{data, presenze}` | ritiro (segreteria); presenze (istruttore) |
| GET | `/api/persone?q=` | ricerca nell'anagrafica (segreteria, coordinatori) |
| POST · GET | `/api/accessi/passaggi` `{codice}` · `?quanti=` | tutti: passaggio al tornello (esito, persona, motivi); passaggi di oggi |
| GET | `/api/accessi/persone/{id}` | segreteria: braccialetti, ban, golden pass, passaggi, esito «adesso» |
| POST · PUT | `/api/accessi/persone/{id}/braccialetti` · `/api/accessi/braccialetti/{id}/stato` | segreteria: assegna; ATTIVO, SMARRITO, DISATTIVATO |
| POST | `/api/accessi/ban` `{tipo: BAN \| SORVEGLIANZA, personaId \| braccialettoId, motivo, al}` · `/ban/{id}/revoca` | segreteria |
| POST | `/api/accessi/persone/{id}/autorizzazioni` · `/autorizzazioni/{id}/revoca` | permessi firmati: uscita autonoma, accompagnatore |
| GET | `/api/accessi/segnalazioni?dopo=` | passaggi di chi è sotto controllo (avvisi in segreteria) |
| POST | `/api/clienti/abbonamenti/{id}/sospensioni` · `/api/clienti/sospensioni/{id}/annulla` | sospensione di un abbonamento |
| POST | `/api/accessi/persone/{id}/golden-pass` · `/golden-pass/{id}/revoca` | coordinatori |
| GET · PUT | `/api/accessi/regole` · `/{codice}` `{attiva, parametri}` | lettura coordinatori, modifica admin |
| GET · POST · PUT | `/api/account` · `/{id}/password` · `/{id}/disattiva` · `/{id}/riattiva` · `/{id}/email` | account dello staff (permesso ACCOUNT) |
| PUT | `/api/me/password` `{attuale, nuova}` | la propria password |
| GET · POST | `/api/scadenze?entro=` · `/api/scadenze/promemoria` | scadenze; promemoria a mano (permesso CLIENTI) |
| GET · POST · PUT · DELETE | `/api/promemoria` · `/{id}` · `/esegui` | regole e testi dei promemoria, giro adesso |
| PUT | `/api/clienti/{id}/contatti` `{email, telefono}` | contatti del cliente |
| PUT | `/api/clienti/{id}/anagrafica` | anagrafica (codice fiscale controllato e unico) |
| POST · PUT | `/api/clienti/{id}/tutori` · `/api/clienti/legami/{id}` · `/legami/{id}/chiudi` | tutori: aggiungi (esistente o nuovo), modifica, togli |
| POST | `/api/clienti/{id}/consensi` · `/api/clienti/consensi/{id}/revoca` | consensi privacy |
| GET · POST · PUT · DELETE | `/api/ruoli` · `/{codice}` · `/{codice}/assegnazioni` · `/api/assegnazioni/{id}` | ruoli e permessi (permesso RUOLI) |
| POST · GET | `/api/clienti` · `/{id}` | segreteria: nuovo cliente; scheda (registra la consultazione) |
| POST | `/api/clienti/{id}/certificati` · `/tesseramenti` · `/abbonamenti` · `/abbonamenti/{id}/termina` · `/{tipo}/{id}/annulla` | segreteria |
| GET | `/api/foglio-ore/mio?mese=` | il proprio foglio ore (.xlsx) |
| GET | `/api/foglio-ore/persone/{id}?mese=` · `/api/foglio-ore/tutti?mese=` · `/api/foglio-ore/voci` | coordinatori (.xlsx, .zip, colonne del modello) |

Le richieste che modificano dati devono rimandare il token CSRF nell'intestazione `X-XSRF-TOKEN`.
Gli errori seguono il formato «problem details» (RFC 9457): 400 dati non validi, 403 permessi,
404 non trovato, 409 conflitto (es. turni sovrapposti).

## Posta (promemoria)

Il server di posta si configura con le variabili standard di Spring: `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`,
`SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true`; il mittente con
`GESTIONALE_POSTA_MITTENTE`, l'orario del giro con `GESTIONALE_PROMEMORIA_CRON` (di base `0 0 9 * * *`, «-» per
spegnerlo). Senza `SPRING_MAIL_HOST` le email si registrano come «non partite».
