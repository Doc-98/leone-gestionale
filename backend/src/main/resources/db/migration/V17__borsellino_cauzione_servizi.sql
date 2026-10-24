-- Borsellino, braccialetto e servizi a crediti (ADR 0018, parti 7–9).
-- · borsellino della persona, con due saldi: credito in euro (rimborsi; usabile per pagare; può scadere; trasferibile
--   ai familiari) e crediti per i servizi (phon, docce; si comprano con una ricarica). Ogni movimento è una riga.
-- · uscita: soldi restituiti (cauzione, credito in denaro), per la cassa
-- · cauzione del braccialetto: un addebito alla consegna, restituita (in denaro o nel borsellino) alla riconsegna
-- · servizi (phon, doccia) con prezzo in crediti e usi gratuiti al giorno; dispositivi (le macchine e i lettori del
--   saldo) con una chiave propria; utilizzi registrati
-- Tutte le regole sono impostazioni della società.

create table regole_borsellino (
    id                   smallint primary key default 1 check (id = 1),
    -- quanto vale un credito dei servizi, e la ricarica minima (in euro)
    valore_credito       numeric(6, 2) not null default 0.20 check (valore_credito > 0),
    ricarica_minima      numeric(9, 2) not null default 5 check (ricarica_minima >= 0),
    -- per quanti mesi vale il credito in euro (null = non scade)
    validita_euro_mesi   int check (validita_euro_mesi between 1 and 120),
    trasferibile         boolean not null default true,
    restituibile         boolean not null default false,
    -- cauzione del braccialetto (0 = nessuna) e come si restituisce alla riconsegna
    cauzione             numeric(9, 2) not null default 0 check (cauzione >= 0),
    cauzione_riconsegna  varchar(12) not null default 'A_SCELTA' check (cauzione_riconsegna in ('DENARO', 'BORSELLINO', 'A_SCELTA')),
    -- riconsegnando l'ultimo braccialetto si rimborsano i crediti dei servizi
    crediti_riconsegna   boolean not null default true,
    -- se il braccialetto si perde, i crediti dei servizi restano alla persona o si perdono
    crediti_smarrimento  varchar(8) not null default 'RESTANO' check (crediti_smarrimento in ('RESTANO', 'PERSI'))
);
insert into regole_borsellino default values;

-- Il borsellino come metodo di pagamento (uno solo)
alter table metodo_pagamento add column borsellino boolean not null default false;
create unique index metodo_pagamento_un_borsellino on metodo_pagamento (borsellino) where borsellino;
insert into metodo_pagamento (nome, da_incassare, ordine, borsellino) values ('Borsellino', false, 6, true);

-- La cauzione del braccialetto è un addebito
alter table addebito drop constraint addebito_origine_check;
alter table addebito add constraint addebito_origine_check
    check (origine in ('ABBONAMENTO', 'TESSERAMENTO', 'ALTRO', 'RIMBORSO', 'CAUZIONE', 'RICARICA'));

create table uscita (
    id                   uuid primary key default gen_random_uuid(),
    persona_id           uuid not null references persona (id),
    data                 date not null,
    importo              numeric(9, 2) not null check (importo > 0),
    metodo_id            uuid not null references metodo_pagamento (id),
    descrizione          varchar(200) not null,
    registrato_da        uuid references persona (id),
    registrato_il        timestamptz not null default now(),
    annullato_il         timestamptz,
    annullato_da         uuid references persona (id),
    motivo_annullamento  varchar(300)
);
create index uscita_data on uscita (data);
create index uscita_persona on uscita (persona_id);

create table movimento_borsellino (
    id              uuid primary key default gen_random_uuid(),
    persona_id      uuid not null references persona (id),
    saldo           varchar(8) not null check (saldo in ('EURO', 'CREDITI')),
    quantita        numeric(9, 2) not null check (quantita <> 0),
    causale         varchar(14) not null check (causale in ('RICARICA', 'CONSUMO', 'ACCREDITO', 'PAGAMENTO', 'TRASFERIMENTO', 'RIMBORSO',
                                                            'SCADENZA', 'SMARRIMENTO', 'CORREZIONE', 'ANNULLAMENTO')),
    descrizione     varchar(200) not null,
    -- per gli euro in entrata: fino a quando valgono (null = non scadono)
    scadenza        date,
    pagamento_id    uuid references pagamento (id),
    uscita_id       uuid references uscita (id),
    controparte_id  uuid references persona (id),
    riferimento_id  uuid,
    creato_da       uuid references persona (id),
    creato_il       timestamptz not null default now(),
    check (saldo <> 'CREDITI' or quantita = trunc(quantita))
);
create index movimento_borsellino_persona on movimento_borsellino (persona_id, saldo);
create index movimento_borsellino_pagamento on movimento_borsellino (pagamento_id);

-- ── Servizi a crediti e dispositivi ─────────────────────────────────────────

create table servizio (
    id                  uuid primary key default gen_random_uuid(),
    nome                varchar(60) not null check (char_length(trim(nome)) > 0),
    crediti             int not null default 1 check (crediti >= 0),
    gratuiti_al_giorno  int not null default 0 check (gratuiti_al_giorno >= 0),
    -- facoltativo, da mostrare sulla macchina (es. il phon va per 5 minuti)
    minuti              int check (minuti > 0),
    attivo              boolean not null default true,
    ordine              int not null default 0
);
create unique index servizio_nome on servizio (lower(nome));

create table dispositivo (
    id               uuid primary key default gen_random_uuid(),
    nome             varchar(80) not null check (char_length(trim(nome)) > 0),
    -- SERVIZIO: scala i crediti (phon, docce); SALDO: mostra solo il saldo (lettore all'ingresso)
    tipo             varchar(10) not null check (tipo in ('SERVIZIO', 'SALDO')),
    servizio_id      uuid references servizio (id),
    -- SHA-256 della chiave (la chiave si mostra una volta sola, alla creazione)
    chiave_hash      varchar(64) not null,
    attivo           boolean not null default true,
    creato_il        timestamptz not null default now(),
    ultimo_contatto  timestamptz,
    check (tipo <> 'SERVIZIO' or servizio_id is not null)
);

create table utilizzo (
    id              uuid primary key default gen_random_uuid(),
    dispositivo_id  uuid not null references dispositivo (id),
    servizio_id     uuid references servizio (id),
    codice          varchar(40) not null,
    persona_id      uuid references persona (id),
    istante         timestamptz not null,
    esito           varchar(16) not null check (esito in ('OK', 'GRATIS', 'CREDITI_FINITI', 'SCONOSCIUTO', 'NON_ATTIVO', 'SERVIZIO_SPENTO')),
    crediti         int not null default 0,
    -- es. il numero del phon scelto sulla centralina
    postazione      varchar(20),
    simulato        boolean not null default false
);
create index utilizzo_persona on utilizzo (persona_id, servizio_id, istante);
create index utilizzo_dispositivo on utilizzo (dispositivo_id, istante);

-- ── Braccialetto riconsegnato ───────────────────────────────────────────────
alter table braccialetto drop constraint braccialetto_stato_check;
alter table braccialetto add constraint braccialetto_stato_check check (stato in ('ATTIVO', 'SMARRITO', 'DISATTIVATO', 'RESTITUITO'));
-- un codice riconsegnato si può dare a un'altra persona, come uno disattivato
drop index braccialetto_codice_in_uso;
create unique index braccialetto_codice_in_uso on braccialetto (codice) where stato not in ('DISATTIVATO', 'RESTITUITO');

-- Nuovi permessi: ricariche e trasferimenti (segreteria e coordinatori); restituire in denaro e correggere i saldi (coordinatori)
insert into ruolo_permesso (ruolo, permesso) values
    ('COORDINATORE', 'BORSELLINO'), ('SEGRETERIA', 'BORSELLINO'), ('COORDINATORE', 'BORSELLINO_RIMBORSI');
