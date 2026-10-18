-- Fase 1: pianificazione dei turni dello staff.
-- · turni «scoperti» (senza persona) da assegnare
-- · nessuna sovrapposizione di turni per la stessa persona (vincolo nel database)
-- · modello settimanale da cui generare i turni del mese
-- · richieste di cambio orario, sostituzione, scambio
-- · tariffe orarie per mansione (con eventuale eccezione per persona)

create extension if not exists btree_gist;

-- ── Turni ───────────────────────────────────────────────────────────────────

alter table turno alter column persona_id drop not null;
alter table turno add column aggiornato_il timestamptz;

-- La stessa persona non può avere due turni pianificati che si accavallano
alter table turno add constraint turno_senza_sovrapposizioni
    exclude using gist (
        persona_id with =,
        tsrange(data + inizio, data + fine) with &&
    ) where (stato = 'PIANIFICATO' and persona_id is not null);

-- ── Modello settimanale ─────────────────────────────────────────────────────

create table modello_settimanale (
    id           uuid primary key default gen_random_uuid(),
    impianto_id  uuid not null references impianto (id),
    nome         varchar(120) not null,
    valido_dal   date not null,
    valido_al    date,
    check (valido_al is null or valido_al >= valido_dal)
);

-- Una riga = una postazione, in un giorno della settimana (1 = lunedì … 7 = domenica), con un orario
-- e la persona che di solito la copre (facoltativa: senza persona il turno nasce scoperto)
create table modello_riga (
    id              uuid primary key default gen_random_uuid(),
    modello_id      uuid not null references modello_settimanale (id) on delete cascade,
    postazione_id   uuid not null references postazione (id),
    giorno_settimana smallint not null check (giorno_settimana between 1 and 7),
    inizio          time not null,
    fine            time not null,
    persona_id      uuid references persona (id),
    check (fine > inizio)
);
create index modello_riga_modello on modello_riga (modello_id);

alter table turno add column modello_riga_id uuid references modello_riga (id) on delete set null;

-- ── Richieste ───────────────────────────────────────────────────────────────

create table richiesta_turno (
    id                 uuid primary key default gen_random_uuid(),
    -- CAMBIO_ORARIO: nuovo orario per il proprio turno
    -- SOSTITUZIONE: un collega prende il turno (indicato, oppure «aperta» a chiunque)
    -- SCAMBIO: due turni si scambiano le persone
    tipo               varchar(20) not null check (tipo in ('CAMBIO_ORARIO', 'SOSTITUZIONE', 'SCAMBIO')),
    turno_id           uuid not null references turno (id),
    richiedente_id     uuid not null references persona (id),
    -- SOSTITUZIONE: chi prende il turno (null finché nessuno si offre)
    collega_id         uuid references persona (id),
    -- SCAMBIO: il turno del collega
    turno_collega_id   uuid references turno (id),
    -- CAMBIO_ORARIO: orario proposto
    nuovo_inizio       time,
    nuovo_fine         time,
    messaggio          varchar(500),
    -- IN_ATTESA_COLLEGA → IN_ATTESA_APPROVAZIONE → APPROVATA | RESPINTA ; ANNULLATA dal richiedente
    stato              varchar(30) not null check (stato in
                         ('IN_ATTESA_COLLEGA', 'IN_ATTESA_APPROVAZIONE', 'APPROVATA', 'RESPINTA', 'ANNULLATA')),
    creata_il          timestamptz not null default now(),
    decisa_da          uuid references persona (id),
    decisa_il          timestamptz,
    nota_decisione     varchar(500),
    check (tipo <> 'CAMBIO_ORARIO' or (nuovo_inizio is not null and nuovo_fine is not null and nuovo_fine > nuovo_inizio)),
    check (tipo <> 'SCAMBIO' or (collega_id is not null and turno_collega_id is not null))
);
create index richiesta_turno_stato on richiesta_turno (stato);
create index richiesta_turno_richiedente on richiesta_turno (richiedente_id);
create index richiesta_turno_collega on richiesta_turno (collega_id);
-- Al massimo una richiesta aperta per turno
create unique index richiesta_turno_una_aperta on richiesta_turno (turno_id)
    where stato in ('IN_ATTESA_COLLEGA', 'IN_ATTESA_APPROVAZIONE');

-- ── Tariffe ─────────────────────────────────────────────────────────────────

-- Compenso orario per mansione; con persona_id è un'eccezione per quella persona
create table tariffa (
    id           uuid primary key default gen_random_uuid(),
    mansione_id  uuid not null references mansione (id),
    persona_id   uuid references persona (id),
    euro_ora     numeric(8, 2) not null check (euro_ora >= 0),
    dal          date not null,
    al           date,
    check (al is null or al >= dal)
);
create index tariffa_mansione on tariffa (mansione_id);
