-- Cassa, i controlli (ADR 0018, parte 11, dopo il confronto con le pratiche del settore): conta alla cieca, differenza
-- tollerata, controllo delle chiusure da parte di un responsabile, riscontro con il POS, avviso se una cassa resta
-- aperta, spese con la cassa sì o no, conto della cassaforte e dei versamenti in banca.

-- cieca: chi chiude conta senza vedere i contanti attesi (il confronto arriva dopo il conteggio).
-- tolleranza: fino a questa differenza (in più o in meno) si chiude e basta; oltre la nota è obbligatoria e la chiusura
--             va controllata (0 = ogni differenza).
-- controllo: quali chiusure deve segnare un responsabile: NESSUNA, OLTRE (oltre la tolleranza o con un riscontro che
--            non torna), TUTTE.
-- spese: si possono pagare piccole spese con la cassa.
-- banca: si tiene il conto di quello che va in cassaforte e di quando va in banca (da banca_dal in poi).
-- ore_avviso: dopo quante ore una cassa aperta fa scattare l'avviso (0 = mai).
alter table impostazioni_cassa
    add column cieca       boolean       not null default true,
    add column tolleranza  numeric(9, 2) not null default 5.00 check (tolleranza >= 0),
    add column controllo   varchar(10)   not null default 'OLTRE' check (controllo in ('NESSUNA', 'OLTRE', 'TUTTE')),
    add column spese       boolean       not null default true,
    add column banca       boolean       not null default false,
    add column banca_dal   timestamptz,
    add column ore_avviso  int           not null default 14 check (ore_avviso between 0 and 72);

-- Quali metodi si riscontrano alla chiusura con lo scontrino del POS (di base il bancomat).
alter table metodo_pagamento add column riscontro boolean not null default false;
update metodo_pagamento set riscontro = true where lower(nome) = 'bancomat';

-- I versamenti in banca: i contanti messi in cassaforte alle chiusure, portati in banca un certo giorno.
create table versamento_banca (
    id             uuid primary key,
    data           date          not null,
    importo        numeric(11, 2) not null check (importo > 0),
    distinta       varchar(60),
    note           varchar(300),
    registrato_da  uuid          references persona (id),
    registrato_il  timestamptz   not null,
    annullato_il   timestamptz,
    annullato_da   uuid          references persona (id)
);

-- primo_conteggio: il primo conteggio (al confronto), se poi si è ricontato.
-- riscontri: per ogni metodo da riscontrare, atteso, scritto dallo scontrino del POS e differenza (fotografati).
-- da_controllare: alla chiusura, secondo le impostazioni; controllata_*: chi l'ha controllata, quando e con che nota.
-- per_cassaforte: quanto è finito in cassaforte (versato alla chiusura + versamenti durante il turno);
-- versamento_id: il versamento in banca che l'ha portato via.
alter table sessione_cassa
    add column primo_conteggio  numeric(9, 2),
    add column riscontri        jsonb,
    add column da_controllare   boolean not null default false,
    add column controllata_il   timestamptz,
    add column controllata_da   uuid references persona (id),
    add column nota_controllo   varchar(500),
    add column per_cassaforte   numeric(9, 2),
    add column versamento_id    uuid references versamento_banca (id);
update sessione_cassa set per_cassaforte = versato where chiusa_il is not null;
create index sessione_cassa_da_controllare on sessione_cassa (chiusa_il) where da_controllare and controllata_il is null;
create index sessione_cassa_cassaforte on sessione_cassa (chiusa_il) where versamento_id is null and per_cassaforte > 0;
