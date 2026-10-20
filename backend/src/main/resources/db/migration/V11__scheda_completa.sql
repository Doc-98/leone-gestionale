-- Scheda cliente completa (ADR 0014): anagrafica estesa, tutori e nucleo famiglia, consensi privacy.

-- ── Anagrafica ──────────────────────────────────────────────────────────────
-- Solo ciò che serve: codice fiscale e indirizzo per ricevute e tesseramenti federali, luogo di nascita e sesso per
-- il codice fiscale. Niente documenti d'identità, professione o dati sanitari.
alter table persona add column sesso varchar(1) check (sesso in ('M', 'F'));
alter table persona add column luogo_nascita varchar(80);
alter table persona add column codice_fiscale varchar(16) check (codice_fiscale ~ '^[A-Z0-9]{16}$');
alter table persona add column indirizzo varchar(150);
alter table persona add column cap varchar(10);
alter table persona add column citta varchar(80);
alter table persona add column provincia varchar(2);
alter table persona add column nazione varchar(60);
alter table persona add column note varchar(1000);
create unique index persona_codice_fiscale_unico on persona (codice_fiscale) where codice_fiscale is not null;

-- ── Tutori ──────────────────────────────────────────────────────────────────
-- Chi è responsabile di una persona (di solito un minore). Il nucleo famiglia si ricava: chi ha un tutore in comune.
create table legame (
    id               uuid primary key default gen_random_uuid(),
    persona_id       uuid not null references persona (id),
    tutore_id        uuid not null references persona (id),
    relazione        varchar(10) not null check (relazione in ('MADRE', 'PADRE', 'TUTORE', 'NONNO', 'ALTRO')),
    -- riceve promemoria e comunicazioni
    comunicazioni    boolean not null default true,
    -- può venire a prenderlo (all'uscita del tornello)
    delegato_ritiro  boolean not null default true,
    dal              date not null default current_date,
    al               date,
    check (persona_id <> tutore_id),
    check (al is null or al >= dal)
);
create unique index legame_unico_attivo on legame (persona_id, tutore_id) where al is null;
create index legame_tutore on legame (tutore_id);

-- ── Consensi privacy ────────────────────────────────────────────────────────
-- TRATTAMENTO: informativa e trattamento per il servizio; MARKETING: comunicazioni promozionali (con i canali);
-- IMMAGINI: foto e video (es. gare, feste). Per i minori firma un genitore.
create table consenso (
    id             uuid primary key default gen_random_uuid(),
    persona_id     uuid not null references persona (id),
    tipo           varchar(12) not null check (tipo in ('TRATTAMENTO', 'MARKETING', 'IMMAGINI')),
    firmato_da     varchar(120),
    dato_il        date not null,
    valido_fino    date,
    email          boolean not null default false,
    sms            boolean not null default false,
    cartaceo       boolean not null default false,
    registrato_da  uuid references persona (id),
    registrato_il  timestamptz not null default now(),
    revocato_il    timestamptz,
    check (valido_fino is null or valido_fino >= dato_il)
);
create index consenso_persona on consenso (persona_id);
