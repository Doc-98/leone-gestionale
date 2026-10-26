-- Un corso non ha un solo istruttore: ha un «parco» di istruttori che ci lavorano, e ogni turno
-- ne ha assegnati uno o più, scelti tra quelli del parco.
create table corso_istruttore (
    corso_id   uuid not null references corso (id) on delete cascade,
    persona_id uuid not null references persona (id),
    primary key (corso_id, persona_id)
);

create table lezione_istruttore (
    lezione_id uuid not null references lezione (id) on delete cascade,
    persona_id uuid not null references persona (id),
    primary key (lezione_id, persona_id)
);

-- Il vecchio istruttore unico diventa il primo del parco e del turno.
insert into corso_istruttore (corso_id, persona_id)
select id, istruttore_id from corso where istruttore_id is not null;

insert into corso_istruttore (corso_id, persona_id)
select distinct l.corso_id, l.istruttore_id from lezione l
where l.istruttore_id is not null
on conflict do nothing;

insert into lezione_istruttore (lezione_id, persona_id)
select l.id, coalesce(l.istruttore_id, c.istruttore_id)
from lezione l join corso c on c.id = l.corso_id
where coalesce(l.istruttore_id, c.istruttore_id) is not null;

-- Un turno si ripete ogni settimana oppure una volta al mese (lo stesso giorno della settimana nella stessa
-- posizione del mese della prima data: per esempio il quarto mercoledì). Un turno «una volta sola» ha
-- valida_dal = valida_al.
alter table lezione add column cadenza varchar(12) not null default 'SETTIMANALE'
    check (cadenza in ('SETTIMANALE', 'MENSILE'));
