-- Cassa (ADR 0018, parte 11): le casse, le scelte su fondo e chiusura, le aperture e chiusure con i contati, i soldi
-- tolti o messi a mano, e da quale cassa è passato ogni pagamento.

-- Quali metodi finiscono nel cassetto (di solito solo i contanti): servono per i contanti attesi alla chiusura.
alter table metodo_pagamento add column contanti boolean not null default false;
update metodo_pagamento set contanti = true where nome ilike '%contant%';

-- Le casse: una sola, o una per postazione (es. due segreterie). La principale prende i pagamenti senza cassa.
create table cassa (
    id          uuid primary key,
    nome        varchar(40) not null check (char_length(trim(nome)) > 0),
    attiva      boolean     not null default true,
    principale  boolean     not null default false,
    ordine      int         not null default 0
);
create unique index cassa_nome on cassa (lower(nome));
create unique index cassa_una_principale on cassa (principale) where principale;

insert into cassa (id, nome, attiva, principale, ordine) values
    ('00000000-0000-0000-0000-00000000ca01', 'Segreteria', true, true, 0);

-- Le scelte della cassa (una riga sola).
-- casse: UNICA (tutti i pagamenti nella principale) o PER_POSTAZIONE (ogni computer dice di quale cassa è).
-- fondo: FISSO (sempre lo stesso importo), RIPORTATO (si riparte da quello lasciato alla chiusura prima),
--        CONTATO (si riconta a ogni apertura e si vede se torna con quello lasciato).
-- chiusura: TURNO (chi entra in cassa la apre e la chiude a fine turno) o GIORNATA (una chiusura a fine giornata,
--           senza apertura).
create table impostazioni_cassa (
    id             smallint primary key check (id = 1),
    casse          varchar(15)    not null check (casse in ('UNICA', 'PER_POSTAZIONE')),
    fondo          varchar(10)    not null check (fondo in ('FISSO', 'RIPORTATO', 'CONTATO')),
    importo_fondo  numeric(9, 2)  not null check (importo_fondo >= 0),
    chiusura       varchar(10)    not null check (chiusura in ('TURNO', 'GIORNATA'))
);
insert into impostazioni_cassa values (1, 'UNICA', 'RIPORTATO', 100.00, 'TURNO');

-- Da quale cassa è passato un pagamento o un'uscita, come l'ha detto il computer (vuoto, o una cassa che non c'è più =
-- la principale). Niente chiave esterna: il modulo pagamenti non conosce le casse.
alter table pagamento add column cassa_id uuid;
alter table uscita add column cassa_id uuid;
create index pagamento_registrato_il on pagamento (registrato_il);
create index uscita_registrato_il on uscita (registrato_il);

-- Un'apertura e chiusura di una cassa. Conta quello che è passato da «dal» (la chiusura prima, o l'inizio del giorno
-- per la prima) alla chiusura: i pagamenti fatti a cassa chiusa vanno nella sessione dopo.
-- fondo: con cui si è partiti; fondo_atteso: quello lasciato alla chiusura prima (per vedere se torna, fondo CONTATO).
-- Alla chiusura: attesi (fondo + contanti entrati − usciti ± movimenti), contati, differenza, lasciato per la
-- prossima volta e versato (tolto dal cassetto: banca, cassaforte); riepilogo = i totali per metodo, fotografati.
create table sessione_cassa (
    id             uuid primary key,
    cassa_id       uuid          not null references cassa (id),
    dal            timestamptz   not null,
    aperta_il      timestamptz   not null,
    aperta_da      uuid          references persona (id),
    esplicita      boolean       not null,
    fondo          numeric(9, 2) not null check (fondo >= 0),
    fondo_atteso   numeric(9, 2),
    chiusa_il      timestamptz,
    chiusa_da      uuid          references persona (id),
    attesi         numeric(9, 2),
    contati        numeric(9, 2) check (contati >= 0),
    differenza     numeric(9, 2),
    lasciato       numeric(9, 2) check (lasciato >= 0),
    versato        numeric(9, 2) check (versato >= 0),
    note           varchar(1000),
    riepilogo      jsonb,
    check ((chiusa_il is null) = (contati is null))
);
create unique index sessione_cassa_una_aperta on sessione_cassa (cassa_id) where chiusa_il is null;
create index sessione_cassa_chiusa on sessione_cassa (cassa_id, chiusa_il desc);

-- Soldi tolti o messi a mano durante una sessione: versamento in banca o in cassaforte, piccola spesa pagata con la
-- cassa, aggiunta (es. monete per il resto).
create table movimento_cassa (
    id             uuid primary key,
    sessione_id    uuid          not null references sessione_cassa (id),
    tipo           varchar(10)   not null check (tipo in ('VERSAMENTO', 'SPESA', 'AGGIUNTA')),
    importo        numeric(9, 2) not null check (importo > 0),
    descrizione    varchar(200)  not null,
    registrato_da  uuid          references persona (id),
    registrato_il  timestamptz   not null,
    annullato_il   timestamptz
);
create index movimento_cassa_sessione on movimento_cassa (sessione_id);
