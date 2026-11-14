-- Livelli dei corsi (ADR 0026): il livello successivo di ogni corso, l'esito di fine stagione di ogni iscritto e il
-- passaggio di livello durante l'anno (il bambino tiene il suo turno ed è in lista d'attesa nel livello nuovo, davanti a
-- chi aspetta senza essere passato).

alter table corso add column livello_successivo_id uuid references corso (id);
alter table corso add constraint corso_livello_successivo_diverso check (livello_successivo_id is null or livello_successivo_id <> id);

-- Un esito per persona, corso e stagione: «passa al livello successivo» o «continua nello stesso livello» (mai «bocciato»)
create table esito_livello (
    id           uuid primary key,
    stagione_id  uuid not null references stagione (id),
    persona_id   uuid not null references persona (id),
    corso_id     uuid not null references corso (id),
    esito        varchar(10) not null check (esito in ('PASSA', 'CONTINUA')),
    deciso_da    uuid references persona (id),
    deciso_il    timestamptz not null,
    unique (stagione_id, persona_id, corso_id)
);
create index esito_livello_corso on esito_livello (corso_id, stagione_id);

-- «Passa adesso al livello successivo»: resta aperto finché non entra in un turno del livello nuovo (o si annulla)
create table passaggio_livello (
    id             uuid primary key,
    persona_id     uuid not null references persona (id),
    iscrizione_id  uuid not null references iscrizione (id) on delete cascade,
    da_corso_id    uuid not null references corso (id),
    a_corso_id     uuid not null references corso (id),
    deciso_da      uuid references persona (id),
    deciso_il      timestamptz not null,
    stato          varchar(10) not null default 'IN_ATTESA' check (stato in ('IN_ATTESA', 'FATTO', 'ANNULLATO')),
    chiuso_il      timestamptz,
    check ((stato = 'IN_ATTESA') = (chiuso_il is null)),
    check (da_corso_id <> a_corso_id)
);
create unique index passaggio_livello_aperto on passaggio_livello (iscrizione_id) where stato = 'IN_ATTESA';

-- Le voci di lista d'attesa nate da un passaggio vengono prima delle altre
alter table attesa add column passaggio_id uuid references passaggio_livello (id) on delete set null;
create index attesa_passaggio on attesa (passaggio_id) where passaggio_id is not null;

-- Il permesso «Livelli» (esiti e passaggi di tutti i corsi; gli istruttori li danno comunque per i loro turni)
insert into ruolo_permesso (ruolo, permesso) values ('COORDINATORE', 'LIVELLI');
