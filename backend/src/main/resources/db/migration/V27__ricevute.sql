-- Ricevute (ADR 0018, parte 10): serie numerate, impostazioni, e le ricevute fatte dai pagamenti.

-- Una serie ha la sua numerazione: formato con {stagione}, {anno} e {numero}, quante cifre, quando riparte da 1.
-- prossimo_numero: il numero da cui partire nel periodo in corso, finché il periodo non ha ancora ricevute (per
-- continuare la numerazione del gestionale di prima); poi si ignora.
create table serie_ricevute (
    id              uuid primary key,
    nome            varchar(60)  not null,
    tipo            varchar(10)  not null check (tipo in ('RICEVUTA', 'FATTURA')),
    attiva          boolean      not null,
    formato         varchar(40)  not null,
    cifre           smallint     not null check (cifre between 1 and 8),
    riparte         varchar(10)  not null check (riparte in ('STAGIONE', 'ANNO', 'MAI')),
    prossimo_numero integer      check (prossimo_numero > 0),
    ordine          smallint     not null default 0
);

insert into serie_ricevute (id, nome, tipo, attiva, formato, cifre, riparte, ordine) values
    ('00000000-0000-0000-0000-00000000e001', 'Ricevute', 'RICEVUTA', true,  '{stagione}-{numero}', 4, 'STAGIONE', 0),
    ('00000000-0000-0000-0000-00000000e002', 'Fatture',  'FATTURA',  false, 'F{anno}/{numero}',    4, 'ANNO',     1);

-- Le scelte valide per tutte le ricevute (una riga sola).
create table impostazioni_ricevute (
    id              smallint     primary key check (id = 1),
    intestata       varchar(12)  not null check (intestata in ('PAGANTE', 'BENEFICIARIO')),
    codici_fiscali  boolean      not null,
    bollo           boolean      not null,
    bollo_soglia    numeric(10, 2) not null,
    bollo_importo   numeric(10, 2) not null,
    dicitura        varchar(1000),
    firma           boolean      not null,
    dopo            varchar(10)  not null check (dopo in ('CHIEDI', 'STAMPA', 'NIENTE')),
    email           boolean      not null,
    famiglia        varchar(10)  not null check (famiglia in ('CHIEDI', 'FAMIGLIA', 'PERSONA'))
);

insert into impostazioni_ricevute values
    (1, 'PAGANTE', true, true, 77.47, 2.00,
     'Ricevuta valida ai fini della detrazione delle spese per attività sportive dei ragazzi tra 5 e 18 anni. Attività svolta da {beneficiario}, nato/a il {nascita}.',
     true, 'CHIEDI', true, 'CHIEDI');

-- Una ricevuta: tutto quello che c'è scritto è copiato al momento, così non cambia se poi cambiano i dati.
-- Il numero di una serie non torna mai libero: una ricevuta annullata resta, con il motivo.
create table ricevuta (
    id                  uuid primary key,
    serie_id            uuid         not null references serie_ricevute (id),
    periodo             varchar(20)  not null,
    numero              integer      not null,
    numero_testo        varchar(60)  not null,
    pagamento_id        uuid         not null,
    data                date         not null,
    intestatario_id     uuid         not null,
    intestatario        jsonb        not null,
    beneficiari         jsonb        not null,
    righe               jsonb        not null,
    totale              numeric(10, 2) not null,
    metodi              varchar(200) not null,
    bollo               numeric(10, 2),
    dicitura            varchar(1500),
    creata_il           timestamptz  not null,
    creata_da           uuid,
    annullata_il        timestamptz,
    annullata_da        uuid,
    motivo_annullamento varchar(300),
    sostituita_da       uuid references ricevuta (id),
    stampata_il         timestamptz,
    inviata_il          timestamptz,
    inviata_a           varchar(320),
    unique (serie_id, periodo, numero)
);

create index ricevuta_pagamento on ricevuta (pagamento_id);
create index ricevuta_intestatario on ricevuta (intestatario_id);
create index ricevuta_beneficiari on ricevuta using gin (beneficiari jsonb_path_ops);
