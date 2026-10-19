-- Fasce orarie definite dai coordinatori, iscrizioni ai turni dei corsi, presenze.
--
-- Il «turno del corso» è il trittico corso + giorno della settimana + orario: nel modello è la riga di `lezione`.
-- Una persona si iscrive a uno o più turni dello stesso corso; le presenze si segnano per turno e data.

-- ── Fasce orarie ────────────────────────────────────────────────────────────
-- Per ogni vasca e giorno della settimana, gli intervalli in cui si divide la giornata (es. 17:00-17:45), con
-- l'assetto delle corsie in quella fascia (5 o 6; null per le vasche senza corsie). Orari liberi.
create table fascia_oraria (
    id                uuid primary key default gen_random_uuid(),
    vasca_id          uuid not null references spazio (id),
    giorno_settimana  smallint not null check (giorno_settimana between 1 and 7),
    inizio            time not null,
    fine              time not null,
    assetto           smallint check (assetto between 1 and 12),
    valida_dal        date not null,
    valida_al         date,
    check (fine > inizio),
    check (valida_al is null or valida_al >= valida_dal)
);
-- Nella stessa vasca e nello stesso giorno le fasce non si accavallano (nei periodi di validità in comune)
alter table fascia_oraria add constraint fascia_senza_sovrapposizioni
    exclude using gist (
        vasca_id with =,
        giorno_settimana with =,
        tsrange(date '2000-01-03' + inizio, date '2000-01-03' + fine) with &&,
        daterange(valida_dal, valida_al, '[]') with &&
    );

-- ── Posti per turno ─────────────────────────────────────────────────────────
alter table lezione add column posti smallint check (posti > 0);

-- ── Iscrizioni ──────────────────────────────────────────────────────────────
create table iscrizione (
    id          uuid primary key default gen_random_uuid(),
    lezione_id  uuid not null references lezione (id),
    persona_id  uuid not null references persona (id),
    dal         date not null,
    al          date,
    creata_il   timestamptz not null default now(),
    check (al is null or al >= dal)
);
create index iscrizione_lezione on iscrizione (lezione_id);
create index iscrizione_persona on iscrizione (persona_id);
-- La stessa persona non è iscritta due volte allo stesso turno nello stesso periodo
alter table iscrizione add constraint iscrizione_senza_doppioni
    exclude using gist (lezione_id with =, persona_id with =, daterange(dal, al, '[]') with &&);

-- ── Presenze ────────────────────────────────────────────────────────────────
create table presenza (
    lezione_id  uuid not null references lezione (id),
    data        date not null,
    persona_id  uuid not null references persona (id),
    presente    boolean not null,
    segnata_da  uuid references persona (id),
    segnata_il  timestamptz not null default now(),
    primary key (lezione_id, data, persona_id)
);
