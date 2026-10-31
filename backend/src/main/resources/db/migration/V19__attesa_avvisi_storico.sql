-- Lista d'attesa (ADR 0015, ottobre 2026): chi è avvisato di un posto ha un certo numero di giorni per rispondere;
-- l'avviso può essere un'email o una telefonata; chi non risponde resta in lista ma il posto passa al prossimo.
alter table attesa
    add column avviso_a_voce      boolean not null default false,
    add column risposta_entro     date,
    add column senza_risposta_il  timestamptz,
    add column conferma_chiesta_il timestamptz,
    add column confermata_il      timestamptz;

-- I giorni per rispondere a un avviso: un'impostazione della società.
alter table societa
    add column giorni_risposta_attesa smallint not null default 3 check (giorni_risposta_attesa between 1 and 30);

-- Lo storico delle liste d'attesa: chi è stato aggiunto, avvisato, iscritto, tolto, e da chi.
create table attesa_evento (
    id         uuid primary key,
    attesa_id  uuid not null references attesa (id) on delete cascade,
    tipo       varchar(30) not null,
    quando     timestamptz not null,
    da         uuid references persona (id)
);
create index attesa_evento_quando on attesa_evento (quando desc);
