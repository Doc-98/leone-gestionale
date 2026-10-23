-- Listino e regole di prezzo per stagione (ADR 0018, parti 3 e 4). Il gestionale propone l'importo; chi ha il permesso
-- «Cambiare l'importo proposto» lo può cambiare, scrivendo il motivo.

-- ── Offerte del listino ─────────────────────────────────────────────────────
-- CORSO: abbonamento a uno o più corsi (anche il nuoto libero) con prezzo per frequenza;
-- CARNET: pacchetto di ingressi con validità, a entrata libera o negli orari di un corso;
-- ALTRO: importi che non danno accesso (es. quota della squadra agonistica).
create table offerta (
    id                 uuid primary key default gen_random_uuid(),
    stagione_id        uuid not null references stagione (id) on delete cascade,
    nome               varchar(120) not null check (char_length(trim(nome)) > 0),
    tipo               varchar(10) not null check (tipo in ('CORSO', 'CARNET', 'ALTRO')),
    descrizione        varchar(300),
    ingressi           smallint check (ingressi > 0),
    validita_mesi      smallint check (validita_mesi between 1 and 36),
    validita_giorni    smallint check (validita_giorni between 1 and 1000),
    -- carnet valido solo negli orari dei turni di questo corso (es. «Nuoto libero»)
    corso_id           uuid references corso (id),
    -- si applica la riduzione per iscrizione a stagione iniziata
    riduzione_tardiva  boolean not null default false,
    attiva             boolean not null default true,
    ordine             integer not null default 0,
    check ((tipo = 'CARNET') = (ingressi is not null)),
    check (validita_mesi is null or validita_giorni is null)
);
create index offerta_stagione on offerta (stagione_id);

-- i corsi a cui vale un'offerta di tipo CORSO (es. «Scuola nuoto»: Stella Marina, Delfino Giallo, …)
create table offerta_corso (
    offerta_id  uuid not null references offerta (id) on delete cascade,
    corso_id    uuid not null references corso (id),
    primary key (offerta_id, corso_id)
);

-- prezzi: per frequenza (1–7 volte a settimana, 0 = open, null = prezzo unico) e, se serve, per gruppo di clienti
create table offerta_prezzo (
    id          uuid primary key default gen_random_uuid(),
    offerta_id  uuid not null references offerta (id) on delete cascade,
    volte       smallint check (volte between 0 and 7),
    gruppo_id   uuid references gruppo_clienti (id),
    importo     numeric(8, 2) not null check (importo >= 0)
);
create unique index offerta_prezzo_unico on offerta_prezzo
    (offerta_id, coalesce(volte, -1), coalesce(gruppo_id, '00000000-0000-0000-0000-000000000000'));

-- ── Regole di prezzo della stagione ─────────────────────────────────────────
-- RIDUZIONE_TARDIVA {scaglioni: [{dal, percentuale}]}; RATE {numero, primaPercentuale, scadenze: [data]};
-- RECESSO {scaglioni: [{finoAl, percentuale}], nelBorsellino}; SOSPENSIONE {settimaneMinime, settimaneCertificato}.
create table regola_prezzo (
    stagione_id  uuid not null references stagione (id) on delete cascade,
    tipo         varchar(20) not null check (tipo in ('RIDUZIONE_TARDIVA', 'RATE', 'RECESSO', 'SOSPENSIONE')),
    attiva       boolean not null default false,
    parametri    jsonb not null default '{}',
    primary key (stagione_id, tipo)
);

-- ── L'abbonamento ricorda da quale offerta viene e com'è stato deciso l'importo ──
alter table abbonamento add column offerta_id uuid references offerta (id);
alter table abbonamento add column importo_listino numeric(8, 2) check (importo_listino >= 0);
alter table abbonamento add column motivo_importo varchar(300);

-- Nuovo permesso: cambiare l'importo proposto dal listino. Di partenza a coordinatori e segreteria.
insert into ruolo_permesso (ruolo, permesso) values ('COORDINATORE', 'SCONTI'), ('SEGRETERIA', 'SCONTI');
