-- Impostazioni della società, stagioni e gruppi di clienti (ADR 0018, parti 1 e 2): ciò che cambia da una società
-- all'altra diventa un dato, non un valore nel codice.

-- ── La società (una sola riga) ──────────────────────────────────────────────
create table societa (
    id               smallint primary key default 1 check (id = 1),
    nome             varchar(120) not null,
    ragione_sociale  varchar(200),
    partita_iva      varchar(11) check (partita_iva ~ '^[0-9]{11}$'),
    codice_fiscale   varchar(16) check (codice_fiscale ~ '^[A-Z0-9]{11,16}$'),
    indirizzo        varchar(150),
    cap              varchar(10),
    citta            varchar(80),
    provincia        varchar(2),
    email            varchar(254),
    telefono         varchar(40),
    -- quanti giorni prima dell'inizio di una stagione si propone già il suo tesseramento
    giorni_anticipo  smallint not null default 30 check (giorni_anticipo between 0 and 365)
);
insert into societa (nome) values ('La mia società sportiva');

-- ── Stagioni ────────────────────────────────────────────────────────────────
create table stagione (
    id                  uuid primary key default gen_random_uuid(),
    nome                varchar(20) not null unique check (char_length(trim(nome)) > 0),
    dal                 date not null,
    al                  date not null,
    -- quota associativa (tesseramento) della stagione; null = non ancora decisa
    quota               numeric(8, 2) check (quota >= 0),
    quota_rimborsabile  boolean not null default false,
    check (al > dal)
);
-- due stagioni non si accavallano
alter table stagione add constraint stagione_senza_sovrapposizioni exclude using gist (daterange(dal, al, '[]') with &&);

-- il nome della stagione nel tesseramento non è più per forza «2026/27» (c'è chi usa l'anno solare)
alter table tesseramento drop constraint tesseramento_stagione_check;
alter table tesseramento alter column stagione type varchar(20);
alter table tesseramento add constraint tesseramento_stagione_check check (char_length(trim(stagione)) > 0);

-- ── Gruppi di clienti ───────────────────────────────────────────────────────
-- Etichette date alle persone (es. «Alunni dell'Istituto»): per prezzi diversi e precedenza nei rinnovi.
create table gruppo_clienti (
    id           uuid primary key default gen_random_uuid(),
    nome         varchar(80) not null,
    descrizione  varchar(300),
    attivo       boolean not null default true
);
create unique index gruppo_clienti_nome on gruppo_clienti (lower(nome));

create table appartenenza_gruppo (
    id         uuid primary key default gen_random_uuid(),
    gruppo_id  uuid not null references gruppo_clienti (id),
    persona_id uuid not null references persona (id),
    dal        date not null default current_date,
    al         date,
    check (al is null or al >= dal)
);
create unique index appartenenza_gruppo_attiva on appartenenza_gruppo (gruppo_id, persona_id) where al is null;
create index appartenenza_gruppo_persona on appartenenza_gruppo (persona_id);
