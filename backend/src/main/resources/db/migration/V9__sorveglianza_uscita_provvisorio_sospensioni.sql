-- Persone sotto controllo, cooldown dopo un passaggio, uscita autonoma dei minorenni, certificato provvisorio,
-- sospensione degli abbonamenti (ADR 0011).

-- «Sotto controllo» (alert list): come un ban, ma non blocca; la segreteria viene avvisata a ogni passaggio
alter table blocco add column tipo varchar(15) not null default 'BAN' check (tipo in ('BAN', 'SORVEGLIANZA'));

-- Lato del tornello e passaggi da segnalare alla segreteria
alter table passaggio add column verso varchar(8) not null default 'ENTRATA' check (verso in ('ENTRATA', 'USCITA'));
alter table passaggio add column segnalato boolean not null default false;
create index passaggio_segnalati on passaggio (istante desc) where segnalato;
create index passaggio_braccialetto on passaggio (braccialetto_id, istante desc);

-- Permessi firmati: uscita autonoma di un minorenne, accompagnatore (può ripassare durante il cooldown)
create table autorizzazione (
    id           uuid primary key default gen_random_uuid(),
    persona_id   uuid not null references persona (id),
    tipo         varchar(20) not null check (tipo in ('USCITA_AUTONOMA', 'ACCOMPAGNATORE')),
    firmata_da   varchar(120),
    firmata_il   date,
    dal          date not null,
    al           date,
    note         varchar(300),
    creata_da    uuid references persona (id),
    creata_il    timestamptz not null default now(),
    revocata_il  timestamptz,
    revocata_da  uuid references persona (id),
    check (al is null or al >= dal)
);
create index autorizzazione_persona on autorizzazione (persona_id);

-- Nuove regole: la sorveglianza è attiva; cooldown e controllo dell'uscita sono spenti finché non servono
insert into regola_accesso (codice, attiva, parametri) values
    ('SORVEGLIANZA', true, '{}'),
    ('RIPASSAGGIO', false, '{"minuti": 5}'),
    ('USCITA', false, '{"etaMinima": 18}');
update regola_accesso set parametri = '{"giorniMaxProvvisorio": 30}' where codice = 'CERTIFICATO';

-- Certificato provvisorio: una dichiarazione firmata di breve durata in attesa del certificato
alter table certificato_medico drop constraint certificato_medico_tipo_check;
alter table certificato_medico add constraint certificato_medico_tipo_check check (tipo in ('NON_AGONISTICO', 'AGONISTICO', 'PROVVISORIO'));
alter table certificato_medico add column firmato_da varchar(120);

-- Sospensione di un abbonamento: nei giorni sospesi non vale, e la scadenza si allunga di altrettanti giorni
create table sospensione (
    id              uuid primary key default gen_random_uuid(),
    abbonamento_id  uuid not null references abbonamento (id),
    dal             date not null,
    al              date not null,
    motivo          varchar(300),
    creata_da       uuid references persona (id),
    creata_il       timestamptz not null default now(),
    annullata_il    timestamptz,
    check (al >= dal)
);
create index sospensione_abbonamento on sospensione (abbonamento_id);
