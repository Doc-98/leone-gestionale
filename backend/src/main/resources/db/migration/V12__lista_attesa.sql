-- Lista d'attesa dei turni dei corsi (ADR 0015): chi aspetta un posto in un turno pieno, in ordine di arrivo.
create table attesa (
    id           uuid primary key default gen_random_uuid(),
    lezione_id   uuid not null references lezione (id),
    persona_id   uuid not null references persona (id),
    aggiunta_il  timestamptz not null default now(),
    aggiunta_da  uuid references persona (id),
    note         varchar(300),
    -- ultima volta che gli si è scritto che c'è un posto
    avvisata_il  timestamptz,
    stato        varchar(10) not null default 'IN_ATTESA' check (stato in ('IN_ATTESA', 'ISCRITTA', 'RINUNCIA')),
    chiusa_il    timestamptz,
    check ((stato = 'IN_ATTESA') = (chiusa_il is null))
);
create unique index attesa_unica on attesa (lezione_id, persona_id) where stato = 'IN_ATTESA';
create index attesa_lezione on attesa (lezione_id, aggiunta_il) where stato = 'IN_ATTESA';
create index attesa_persona on attesa (persona_id);
