-- Parco istruttori demo (persone inventate). I corsi demo nascono dopo la V18, quindi qui si ripete il suo passaggio:
-- l'istruttore unico del corso e del turno diventa il primo del parco e del turno.
insert into corso_istruttore (corso_id, persona_id)
select id, istruttore_id from corso where istruttore_id is not null
on conflict do nothing;

insert into corso_istruttore (corso_id, persona_id)
select distinct corso_id, istruttore_id from lezione where istruttore_id is not null
on conflict do nothing;

insert into lezione_istruttore (lezione_id, persona_id)
select l.id, coalesce(l.istruttore_id, c.istruttore_id)
from lezione l join corso c on c.id = l.corso_id
where coalesce(l.istruttore_id, c.istruttore_id) is not null
on conflict do nothing;

-- Ogni corso di scuola nuoto ha anche l'istruttore del corso che lo segue in ordine alfabetico, e al sabato i suoi
-- turni sono tenuti in due.
with scuola as (
    select id, istruttore_id, row_number() over (order by nome) as n, count(*) over () as quanti
    from corso where tipo = 'SCUOLA_NUOTO' and istruttore_id is not null
), coppie as (
    select a.id as corso_id, b.istruttore_id as secondo
    from scuola a join scuola b on b.n = a.n % a.quanti + 1
    where b.istruttore_id <> a.istruttore_id
)
insert into corso_istruttore (corso_id, persona_id)
select corso_id, secondo from coppie
on conflict do nothing;

insert into lezione_istruttore (lezione_id, persona_id)
select l.id, ci.persona_id
from lezione l
join corso c on c.id = l.corso_id and c.tipo = 'SCUOLA_NUOTO'
join corso_istruttore ci on ci.corso_id = c.id and ci.persona_id <> c.istruttore_id
where l.giorno_settimana = 6
on conflict do nothing;
