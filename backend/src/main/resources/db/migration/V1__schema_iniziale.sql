-- Schema iniziale: identità, struttura, turni dello staff.
-- Convenzioni: id uuid; nomi in italiano; niente cancellazioni per i dati con storico (date di validità/stato);
-- orari "da calendario" come data + ora locale (fuso Europe/Rome).

-- ── Identità ────────────────────────────────────────────────────────────────

create table persona (
    id            uuid primary key default gen_random_uuid(),
    nome          varchar(80)  not null,
    cognome       varchar(80)  not null,
    data_nascita  date,
    email         varchar(200),
    telefono      varchar(40),
    creato_il     timestamptz  not null default now()
);

create table account (
    id             uuid primary key default gen_random_uuid(),
    persona_id     uuid not null unique references persona (id),
    email          varchar(200) not null,
    password_hash  varchar(200) not null,
    attivo         boolean not null default true,
    creato_il      timestamptz not null default now()
);
-- email unica senza distinguere maiuscole/minuscole
create unique index account_email_unica on account (lower(email));

-- ── Struttura ───────────────────────────────────────────────────────────────

create table disciplina (
    id      uuid primary key default gen_random_uuid(),
    codice  varchar(40) not null unique,
    nome    varchar(80) not null
);

create table sede (
    id         uuid primary key default gen_random_uuid(),
    nome       varchar(120) not null,
    indirizzo  varchar(200)
);

create table impianto (
    id             uuid primary key default gen_random_uuid(),
    sede_id        uuid not null references sede (id),
    disciplina_id  uuid not null references disciplina (id),
    nome           varchar(120) not null
);

create table spazio (
    id           uuid primary key default gen_random_uuid(),
    impianto_id  uuid not null references impianto (id),
    nome         varchar(120) not null,
    -- VASCA, CORSIA, SPOGLIATOIO, ATRIO, ALTRO
    tipo         varchar(20)  not null,
    ordine       int not null default 0
);

-- Ruolo di un account, eventualmente limitato a un impianto (null = tutta la società)
create table assegnazione_ruolo (
    id           uuid primary key default gen_random_uuid(),
    account_id   uuid not null references account (id),
    ruolo        varchar(20) not null check (ruolo in ('ADMIN', 'COORDINATORE', 'SEGRETERIA', 'STAFF')),
    impianto_id  uuid references impianto (id),
    dal          date not null default current_date,
    al           date,
    check (al is null or al >= dal)
);
create index assegnazione_ruolo_account on assegnazione_ruolo (account_id);

-- ── Staff ───────────────────────────────────────────────────────────────────

-- Es. «Assistente spogliatoio», «Assistente atrio», «Istruttore»
create table mansione (
    id           uuid primary key default gen_random_uuid(),
    impianto_id  uuid not null references impianto (id),
    codice       varchar(40)  not null,
    nome         varchar(120) not null,
    unique (impianto_id, codice)
);

-- Es. «Spogliatoio maschile · postazione 2»: le righe dell'attuale foglio turni
create table postazione (
    id           uuid primary key default gen_random_uuid(),
    mansione_id  uuid not null references mansione (id),
    spazio_id    uuid references spazio (id),
    nome         varchar(120) not null,
    ordine       int not null default 0,
    attiva       boolean not null default true
);

create table turno (
    id             uuid primary key default gen_random_uuid(),
    persona_id     uuid not null references persona (id),
    postazione_id  uuid not null references postazione (id),
    data           date not null,
    inizio         time not null,
    fine           time not null,
    -- PIANIFICATO, ANNULLATO
    stato          varchar(20) not null default 'PIANIFICATO' check (stato in ('PIANIFICATO', 'ANNULLATO')),
    note           varchar(500),
    creato_il      timestamptz not null default now(),
    check (fine > inizio)
);
create index turno_persona_data on turno (persona_id, data);
create index turno_data on turno (data);
