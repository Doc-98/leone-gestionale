-- Addebiti e pagamenti (ADR 0018, parte 5).
-- · addebito: cosa una persona deve (iscrizione, rata, quota associativa, altro), con la scadenza; importo negativo =
--   un credito (es. il rimborso per recesso, finché non c'è il borsellino)
-- · pagamento: chi paga (anche un genitore), quanto e quando; può pagare più addebiti, anche di più familiari, e con
--   più metodi insieme (parte contanti, parte carta)
-- · metodo di pagamento: elenco modificabile; quelli «da incassare» (bonifico) si segnano incassati a mano

create table metodo_pagamento (
    id            uuid primary key default gen_random_uuid(),
    nome          varchar(60) not null check (char_length(trim(nome)) > 0),
    da_incassare  boolean not null default false,
    attivo        boolean not null default true,
    ordine        int not null default 0
);
create unique index metodo_pagamento_nome on metodo_pagamento (lower(nome));

insert into metodo_pagamento (nome, da_incassare, ordine) values
    ('Contanti', false, 1), ('Bancomat', false, 2), ('Carta di credito', false, 3), ('Satispay', false, 4), ('Bonifico', true, 5);

create table addebito (
    id                   uuid primary key default gen_random_uuid(),
    persona_id           uuid not null references persona (id),
    descrizione          varchar(200) not null check (char_length(trim(descrizione)) > 0),
    importo              numeric(9, 2) not null check (importo <> 0),
    scadenza             date not null,
    -- ABBONAMENTO, TESSERAMENTO (quota associativa), ALTRO (scritto a mano o dal listino), RIMBORSO (credito)
    origine              varchar(20) not null check (origine in ('ABBONAMENTO', 'TESSERAMENTO', 'ALTRO', 'RIMBORSO')),
    -- l'abbonamento o il tesseramento da cui viene
    riferimento_id       uuid,
    offerta_id           uuid references offerta (id),
    -- rata «numero» di «rate» (null se in un'unica soluzione)
    rata                 int,
    rate                 int,
    note                 varchar(300),
    creato_da            uuid references persona (id),
    creato_il            timestamptz not null default now(),
    annullato_il         timestamptz,
    annullato_da         uuid references persona (id),
    motivo_annullamento  varchar(300),
    check ((rata is null) = (rate is null)),
    check (rata is null or rata between 1 and rate)
);
create index addebito_persona on addebito (persona_id);
create index addebito_riferimento on addebito (origine, riferimento_id);

create table pagamento (
    id                   uuid primary key default gen_random_uuid(),
    pagante_id           uuid not null references persona (id),
    data                 date not null,
    importo              numeric(9, 2) not null check (importo > 0),
    note                 varchar(300),
    registrato_da        uuid references persona (id),
    registrato_il        timestamptz not null default now(),
    annullato_il         timestamptz,
    annullato_da         uuid references persona (id),
    motivo_annullamento  varchar(300)
);
create index pagamento_pagante on pagamento (pagante_id);
create index pagamento_data on pagamento (data);

-- Con quali metodi si è pagato (la somma fa l'importo del pagamento)
create table pagamento_metodo (
    id            uuid primary key default gen_random_uuid(),
    pagamento_id  uuid not null references pagamento (id) on delete cascade,
    metodo_id     uuid not null references metodo_pagamento (id),
    importo       numeric(9, 2) not null check (importo > 0),
    -- null = da incassare (es. bonifico non ancora arrivato sul conto)
    incassato_il  date,
    incassato_da  uuid references persona (id),
    -- es. il CRO del bonifico
    riferimento   varchar(120)
);
create index pagamento_metodo_pagamento on pagamento_metodo (pagamento_id);
create index pagamento_metodo_da_incassare on pagamento_metodo (metodo_id) where incassato_il is null;

-- Quali addebiti paga, e quanto di ciascuno (la somma fa l'importo del pagamento)
create table pagamento_addebito (
    pagamento_id  uuid not null references pagamento (id) on delete cascade,
    addebito_id   uuid not null references addebito (id),
    importo       numeric(9, 2) not null check (importo > 0),
    primary key (pagamento_id, addebito_id)
);
create index pagamento_addebito_addebito on pagamento_addebito (addebito_id);

-- Nuovi permessi: registrare i pagamenti (segreteria e coordinatori) e annullarli (coordinatori)
insert into ruolo_permesso (ruolo, permesso) values
    ('COORDINATORE', 'PAGAMENTI'), ('SEGRETERIA', 'PAGAMENTI'), ('COORDINATORE', 'PAGAMENTI_ANNULLA');
