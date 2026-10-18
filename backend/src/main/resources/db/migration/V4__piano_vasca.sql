-- Fase 2: piano vasca.
-- · corsie dentro le vasche (spazio con un padre)
-- · corsi con lezioni ricorrenti (giorno della settimana + orario, in un periodo) su uno o più spazi
-- · eccezioni alle lezioni: saltata o spostata in una data
-- · eventi puntuali: gara, lezione privata, lezione di prova, chiusura, altro

-- ── Corsie ──────────────────────────────────────────────────────────────────

alter table spazio add column padre_id uuid references spazio (id);
create index spazio_padre on spazio (padre_id);

-- ── Corsi e lezioni ─────────────────────────────────────────────────────────

create table corso (
    id             uuid primary key default gen_random_uuid(),
    impianto_id    uuid not null references impianto (id),
    nome           varchar(120) not null,
    -- SCUOLA_NUOTO, ACQUATICITA, AGONISMO, MASTER, ACQUAGYM, CURRICOLARE, NUOTO_LIBERO, ALTRO
    tipo           varchar(20) not null check (tipo in
                     ('SCUOLA_NUOTO', 'ACQUATICITA', 'AGONISMO', 'MASTER', 'ACQUAGYM', 'CURRICOLARE', 'NUOTO_LIBERO', 'ALTRO')),
    livello        varchar(60),
    eta_da         smallint check (eta_da between 0 and 120),
    eta_a          smallint check (eta_a between 0 and 120),
    istruttore_id  uuid references persona (id),
    note           varchar(500),
    attivo         boolean not null default true,
    creato_il      timestamptz not null default now(),
    check (eta_a is null or eta_da is null or eta_a >= eta_da)
);
create index corso_impianto on corso (impianto_id);

-- Una lezione ricorrente: ogni settimana nel giorno indicato (1 = lunedì … 7 = domenica), nel periodo di validità
create table lezione (
    id                uuid primary key default gen_random_uuid(),
    corso_id          uuid not null references corso (id) on delete cascade,
    giorno_settimana  smallint not null check (giorno_settimana between 1 and 7),
    inizio            time not null,
    fine              time not null,
    valida_dal        date not null,
    valida_al         date,
    -- se diverso da quello del corso
    istruttore_id     uuid references persona (id),
    check (fine > inizio),
    check (valida_al is null or valida_al >= valida_dal)
);
create index lezione_corso on lezione (corso_id);

create table lezione_spazio (
    lezione_id  uuid not null references lezione (id) on delete cascade,
    spazio_id   uuid not null references spazio (id),
    primary key (lezione_id, spazio_id)
);

-- In una data la lezione non si fa (SALTATA) o si fa in un altro giorno/orario (SPOSTATA)
create table lezione_eccezione (
    id            uuid primary key default gen_random_uuid(),
    lezione_id    uuid not null references lezione (id) on delete cascade,
    data          date not null,
    tipo          varchar(10) not null check (tipo in ('SALTATA', 'SPOSTATA')),
    nuova_data    date,
    nuovo_inizio  time,
    nuovo_fine    time,
    motivo        varchar(300),
    unique (lezione_id, data),
    check (tipo <> 'SPOSTATA' or (nuova_data is not null and nuovo_inizio is not null and nuovo_fine is not null and nuovo_fine > nuovo_inizio))
);

-- ── Eventi ──────────────────────────────────────────────────────────────────

create table evento (
    id             uuid primary key default gen_random_uuid(),
    impianto_id    uuid not null references impianto (id),
    tipo           varchar(20) not null check (tipo in ('GARA', 'LEZIONE_PRIVATA', 'LEZIONE_PROVA', 'CHIUSURA', 'ALTRO')),
    titolo         varchar(120) not null,
    data           date not null,
    -- più giorni (es. chiusura per le feste); null = solo «data»
    data_fine      date,
    inizio         time not null,
    fine           time not null,
    istruttore_id  uuid references persona (id),
    note           varchar(500),
    annullato      boolean not null default false,
    creato_il      timestamptz not null default now(),
    check (fine > inizio),
    check (data_fine is null or data_fine >= data)
);
create index evento_periodo on evento (impianto_id, data);

-- Spazi occupati; nessuno spazio = tutto l'impianto (es. chiusura)
create table evento_spazio (
    evento_id  uuid not null references evento (id) on delete cascade,
    spazio_id  uuid not null references spazio (id),
    primary key (evento_id, spazio_id)
);
