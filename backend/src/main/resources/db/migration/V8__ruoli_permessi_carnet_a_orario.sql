-- Ruoli modificabili con i loro permessi (ADR 0010) e carnet legato all'orario di un corso.

-- ── Ruoli e permessi ────────────────────────────────────────────────────────
-- I quattro ruoli di sistema non si cancellano; ADMIN ha sempre tutti i poteri e non ha righe in ruolo_permesso.
create table ruolo (
    codice       varchar(40) primary key check (codice ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    nome         varchar(80) not null,
    descrizione  varchar(300),
    di_sistema   boolean not null default false
);
insert into ruolo (codice, nome, descrizione, di_sistema) values
    ('ADMIN', 'Admin', 'Tutto, compresi ruoli, permessi e regole del tornello', true),
    ('COORDINATORE', 'Coordinatore', 'Orari, piano vasca, richieste, clienti', true),
    ('SEGRETERIA', 'Segreteria', 'Clienti, iscrizioni, abbonamenti, tornello e tutta la gestione', true),
    ('STAFF', 'Staff', 'I propri turni, le richieste, il piano vasca, il tornello', true);

create table ruolo_permesso (
    ruolo     varchar(40) not null references ruolo (codice) on delete cascade,
    permesso  varchar(40) not null,
    primary key (ruolo, permesso)
);
-- Coordinatori e segreteria: tutta la gestione, tranne ruoli e regole del tornello. Lo staff fa i turni.
insert into ruolo_permesso (ruolo, permesso)
select r, p from unnest(array['COORDINATORE', 'SEGRETERIA']) r,
    unnest(array['PIANIFICAZIONE', 'APPROVAZIONI', 'ORE_STAFF', 'ANAGRAFICHE_STAFF', 'PIANO_MODIFICA', 'ISCRIZIONI',
                 'PRESENZE', 'CLIENTI', 'BRACCIALETTI', 'GOLDEN_PASS']) p;
insert into ruolo_permesso (ruolo, permesso) values ('STAFF', 'TURNI');

alter table assegnazione_ruolo drop constraint assegnazione_ruolo_ruolo_check;
alter table assegnazione_ruolo alter column ruolo type varchar(40);
alter table assegnazione_ruolo add constraint assegnazione_ruolo_ruolo_fkey foreign key (ruolo) references ruolo (codice);

-- ── Carnet a orario ─────────────────────────────────────────────────────────
-- Un carnet con corso_id vale solo negli orari dei turni di quel corso (es. «Nuoto libero»); senza, entrata libera.
alter table abbonamento drop constraint abbonamento_check1;
alter table abbonamento add constraint abbonamento_tipo_coerente check (
    (tipo = 'CORSO' and corso_id is not null and volte_a_settimana is not null and ingressi is null)
    or (tipo = 'CARNET' and ingressi is not null and volte_a_settimana is null));
