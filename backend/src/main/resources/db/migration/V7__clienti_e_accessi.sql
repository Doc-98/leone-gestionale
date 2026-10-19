-- Clienti e accessi (ADR 0009): certificati, tesseramento annuale, abbonamenti (corso o carnet), braccialetti,
-- ban, golden pass, regole del tornello, passaggi. Niente si cancella: si annulla (errori) o si chiude con una data.

-- ── Clienti ─────────────────────────────────────────────────────────────────
create table certificato_medico (
    id             uuid primary key default gen_random_uuid(),
    persona_id     uuid not null references persona (id),
    tipo           varchar(20) not null check (tipo in ('NON_AGONISTICO', 'AGONISTICO')),
    rilasciato_il  date,
    -- valido fino a questo giorno compreso
    scadenza       date not null,
    note           varchar(300),
    registrato_da  uuid references persona (id),
    registrato_il  timestamptz not null default now(),
    annullato_il   timestamptz,
    check (rilasciato_il is null or rilasciato_il <= scadenza)
);
create index certificato_persona on certificato_medico (persona_id);

-- Quota annuale per stagione (es. «2026/27», di solito settembre-luglio)
create table tesseramento (
    id             uuid primary key default gen_random_uuid(),
    persona_id     uuid not null references persona (id),
    stagione       varchar(7) not null check (stagione ~ '^[0-9]{4}/[0-9]{2}$'),
    dal            date not null,
    al             date not null,
    importo        numeric(8, 2) check (importo >= 0),
    pagato_il      date,
    registrato_da  uuid references persona (id),
    registrato_il  timestamptz not null default now(),
    annullato_il   timestamptz,
    check (al >= dal)
);
create unique index tesseramento_uno_per_stagione on tesseramento (persona_id, stagione) where annullato_il is null;

-- Abbonamento a un corso (mono, bi, trisettimanale) oppure carnet di ingressi
create table abbonamento (
    id                 uuid primary key default gen_random_uuid(),
    persona_id         uuid not null references persona (id),
    tipo               varchar(10) not null check (tipo in ('CORSO', 'CARNET')),
    corso_id           uuid references corso (id),
    volte_a_settimana  smallint check (volte_a_settimana between 1 and 7),
    ingressi           smallint check (ingressi > 0),
    dal                date not null,
    al                 date,
    importo            numeric(8, 2) check (importo >= 0),
    note               varchar(300),
    registrato_da      uuid references persona (id),
    registrato_il      timestamptz not null default now(),
    annullato_il       timestamptz,
    check (al is null or al >= dal),
    check ((tipo = 'CORSO' and corso_id is not null and volte_a_settimana is not null and ingressi is null)
        or (tipo = 'CARNET' and ingressi is not null and corso_id is null and volte_a_settimana is null))
);
create index abbonamento_persona on abbonamento (persona_id);

-- Gli ingressi scalati da un carnet (uno per passaggio che ha consumato)
create table ingresso_carnet (
    id              uuid primary key default gen_random_uuid(),
    abbonamento_id  uuid not null references abbonamento (id),
    istante         timestamptz not null,
    passaggio_id    uuid
);
create index ingresso_carnet_abbonamento on ingresso_carnet (abbonamento_id, istante desc);

-- Chi ha aperto quale scheda cliente e quando (dati di minori, ADR 0003)
create table consultazione_scheda (
    id             uuid primary key default gen_random_uuid(),
    persona_id     uuid not null references persona (id),
    consultata_da  uuid not null references persona (id),
    istante        timestamptz not null default now()
);
create index consultazione_persona on consultazione_scheda (persona_id, istante desc);

-- ── Accessi ─────────────────────────────────────────────────────────────────
create table braccialetto (
    id            uuid primary key default gen_random_uuid(),
    -- seriale del chip, maiuscolo e senza separatori
    codice        varchar(32) not null check (codice ~ '^[0-9A-Z]{4,32}$'),
    persona_id    uuid not null references persona (id),
    stato         varchar(12) not null default 'ATTIVO' check (stato in ('ATTIVO', 'SMARRITO', 'DISATTIVATO')),
    stato_dal     timestamptz not null default now(),
    assegnato_il  timestamptz not null default now(),
    note          varchar(300)
);
-- Lo stesso codice non è in uso da due persone; un braccialetto disattivato si può riassegnare
create unique index braccialetto_codice_in_uso on braccialetto (codice) where stato <> 'DISATTIVATO';
create index braccialetto_persona on braccialetto (persona_id);

-- Ban di una persona (tutti i suoi braccialetti) o di un singolo braccialetto
create table blocco (
    id               uuid primary key default gen_random_uuid(),
    persona_id       uuid references persona (id),
    braccialetto_id  uuid references braccialetto (id),
    motivo           varchar(300) not null,
    dal              date not null,
    al               date,
    creato_da        uuid references persona (id),
    creato_il        timestamptz not null default now(),
    -- tolto prima della fine prevista
    revocato_il      timestamptz,
    revocato_da      uuid references persona (id),
    check (num_nonnulls(persona_id, braccialetto_id) = 1),
    check (al is null or al >= dal)
);
create index blocco_persona on blocco (persona_id);
create index blocco_braccialetto on blocco (braccialetto_id);

-- I braccialetti della persona danno sempre verde (salvo braccialetto smarrito, disattivato o bannato)
create table golden_pass (
    id          uuid primary key default gen_random_uuid(),
    persona_id  uuid not null references persona (id),
    motivo      varchar(300),
    dal         date not null,
    al          date,
    creato_da   uuid references persona (id),
    creato_il   timestamptz not null default now(),
    revocato_il timestamptz,
    revocato_da uuid references persona (id),
    check (al is null or al >= dal)
);
create index golden_pass_persona on golden_pass (persona_id);

-- Un controllo del tornello: attivo o no, con i suoi parametri
create table regola_accesso (
    codice         varchar(40) primary key,
    attiva         boolean not null default true,
    parametri      jsonb not null default '{}',
    modificata_da  uuid references persona (id),
    modificata_il  timestamptz
);
insert into regola_accesso (codice, parametri) values
    ('BRACCIALETTO', '{}'),
    ('BAN', '{}'),
    ('CERTIFICATO', '{}'),
    ('TESSERAMENTO', '{}'),
    ('ABBONAMENTO', '{"minutiRientro": 90}'),
    ('FASCIA_ORARIA', '{"minutiPrima": 10, "minutiDopoInizio": null}');

-- Ogni passaggio al tornello, verde o rosso, con i motivi
create table passaggio (
    id               uuid primary key default gen_random_uuid(),
    istante          timestamptz not null default now(),
    codice           varchar(32) not null,
    braccialetto_id  uuid references braccialetto (id),
    persona_id       uuid references persona (id),
    verde            boolean not null,
    golden_pass      boolean not null default false,
    motivi           jsonb not null default '[]',
    carnet_id        uuid references abbonamento (id),
    letto_da         uuid references persona (id)
);
create index passaggio_istante on passaggio (istante desc);
create index passaggio_persona on passaggio (persona_id, istante desc);
