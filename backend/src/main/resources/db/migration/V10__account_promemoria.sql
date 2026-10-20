-- Gestione degli account (ADR 0012) e promemoria via email delle scadenze (ADR 0013).

-- ── Account ─────────────────────────────────────────────────────────────────
alter table account add column deve_cambiare_password boolean not null default false;
alter table account add column ultimo_accesso timestamptz;
alter table account add column disattivato_il timestamptz;

-- ── Email inviate (registro) ────────────────────────────────────────────────
create table email_inviata (
    id            uuid primary key default gen_random_uuid(),
    persona_id    uuid references persona (id),
    destinatario  varchar(200),
    oggetto       varchar(200) not null,
    testo         text not null,
    -- INVIATA; NON_INVIATA (server di posta non configurato); SENZA_EMAIL; ERRORE
    stato         varchar(15) not null check (stato in ('INVIATA', 'NON_INVIATA', 'SENZA_EMAIL', 'ERRORE')),
    errore        varchar(500),
    inviata_da    uuid references persona (id),
    creata_il     timestamptz not null default now()
);
create index email_inviata_persona on email_inviata (persona_id, creata_il desc);
create index email_inviata_quando on email_inviata (creata_il desc);

-- ── Promemoria ──────────────────────────────────────────────────────────────
-- CERTIFICATO, ABBONAMENTO, TESSERAMENTO: «giorni» prima della scadenza.
-- TESSERAMENTO_MANCANTE (abbonamento valido ma tesseramento della stagione non pagato): si ripete ogni «giorni»
-- giorni, al massimo «max_invii» volte per stagione.
create table promemoria (
    id         uuid primary key default gen_random_uuid(),
    tipo       varchar(25) not null check (tipo in ('CERTIFICATO', 'ABBONAMENTO', 'TESSERAMENTO', 'TESSERAMENTO_MANCANTE')),
    giorni     smallint not null check (giorni between 0 and 365),
    max_invii  smallint check (max_invii between 1 and 20),
    attivo     boolean not null default true,
    oggetto    varchar(200) not null,
    testo      text not null
);

create table invio_promemoria (
    id             uuid primary key default gen_random_uuid(),
    promemoria_id  uuid references promemoria (id) on delete set null,
    tipo           varchar(25) not null,
    persona_id     uuid not null references persona (id),
    -- cosa riguarda: l'id del certificato o dell'abbonamento, o la stagione
    riferimento    varchar(80) not null,
    scadenza       date,
    email_id       uuid references email_inviata (id),
    manuale        boolean not null default false,
    creato_il      timestamptz not null default now()
);
create index invio_promemoria_rif on invio_promemoria (promemoria_id, riferimento);
create index invio_promemoria_persona on invio_promemoria (persona_id, creato_il desc);

insert into promemoria (tipo, giorni, max_invii, attivo, oggetto, testo) values
('CERTIFICATO', 30, null, true, 'Il certificato medico di {nome} scade il {scadenza}',
'Buongiorno,

il certificato medico di {nome} {cognome} scade il {scadenza}, tra {giorni} giorni.
Senza un certificato valido non sarà possibile accedere alla piscina.

Per rinnovarlo basta portare il nuovo certificato in segreteria.

Grazie,
{societa}'),
('CERTIFICATO', 7, null, true, 'Promemoria: il certificato medico di {nome} scade il {scadenza}',
'Buongiorno,

ricordiamo che il certificato medico di {nome} {cognome} scade il {scadenza}, tra {giorni} giorni.
Dal giorno dopo la scadenza non sarà possibile accedere alla piscina.

Grazie,
{societa}'),
('ABBONAMENTO', 15, null, true, 'L''abbonamento di {nome} scade il {scadenza}',
'Buongiorno,

l''abbonamento {corso} di {nome} {cognome} scade il {scadenza}, tra {giorni} giorni.
Per rinnovarlo e non perdere il posto potete passare in segreteria.

Grazie,
{societa}'),
('TESSERAMENTO', 15, null, false, 'Il tesseramento di {nome} scade il {scadenza}',
'Buongiorno,

il tesseramento {stagione} di {nome} {cognome} scade il {scadenza}.
Per la nuova stagione la quota annuale si rinnova in segreteria.

Grazie,
{societa}'),
('TESSERAMENTO_MANCANTE', 7, 3, true, 'Manca il tesseramento {stagione} di {nome}',
'Buongiorno,

per la stagione {stagione} risulta ancora da pagare la quota annuale di tesseramento di {nome} {cognome}.
Senza il tesseramento non sarà possibile accedere alla piscina, anche con l''abbonamento in regola.

La quota si paga in segreteria.

Grazie,
{societa}');
