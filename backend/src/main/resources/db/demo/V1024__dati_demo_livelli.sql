-- Livelli dei corsi demo (ADR 0026): la catena della scuola nuoto e degli adulti, Luca Bianchi (staff@demo.leone) che
-- insegna anche nel Delfino Giallo, e qualche esito già dato nel suo primo turno.

update corso c set livello_successivo_id = v.dopo::uuid
from (values
    ('00000000-0000-0000-0000-000000000611', '00000000-0000-0000-0000-000000000626'), -- Acquababy 0-1 → 1-2
    ('00000000-0000-0000-0000-000000000626', '00000000-0000-0000-0000-000000000627'), -- 1-2 → 2-3
    ('00000000-0000-0000-0000-000000000627', '00000000-0000-0000-0000-000000000628'), -- 2-3 → ambientamento 3 anni
    ('00000000-0000-0000-0000-000000000628', '00000000-0000-0000-0000-000000000612'), -- → vasca piccola 4 anni
    ('00000000-0000-0000-0000-000000000612', '00000000-0000-0000-0000-000000000613'), -- → vasca alta
    ('00000000-0000-0000-0000-000000000613', '00000000-0000-0000-0000-000000000614'), -- → Stella Marina
    ('00000000-0000-0000-0000-000000000614', '00000000-0000-0000-0000-000000000615'), -- → Delfino Giallo
    ('00000000-0000-0000-0000-000000000615', '00000000-0000-0000-0000-000000000616'), -- → Verde
    ('00000000-0000-0000-0000-000000000616', '00000000-0000-0000-0000-000000000617'), -- → Azzurro
    ('00000000-0000-0000-0000-000000000617', '00000000-0000-0000-0000-000000000618'), -- → Rosso
    ('00000000-0000-0000-0000-000000000618', '00000000-0000-0000-0000-000000000619'), -- → Nero
    ('00000000-0000-0000-0000-000000000619', '00000000-0000-0000-0000-000000000620'), -- → Squalo Base
    ('00000000-0000-0000-0000-000000000620', '00000000-0000-0000-0000-000000000621'), -- → Squalo Avanzato
    ('00000000-0000-0000-0000-000000000637', '00000000-0000-0000-0000-000000000634'), -- Adulti base → Easy Master
    ('00000000-0000-0000-0000-000000000634', '00000000-0000-0000-0000-000000000635'), -- → intermedio
    ('00000000-0000-0000-0000-000000000635', '00000000-0000-0000-0000-000000000636')  -- → avanzato
) as v(corso, dopo)
where c.id = v.corso::uuid
  and exists (select 1 from corso d where d.id = v.dopo::uuid);

-- Luca Bianchi nel parco del Delfino Giallo e della Stella Marina, in quattro turni del primo e due della seconda
insert into corso_istruttore (corso_id, persona_id)
values ('00000000-0000-0000-0000-000000000615', '00000000-0000-0000-0000-000000000103'),
       ('00000000-0000-0000-0000-000000000614', '00000000-0000-0000-0000-000000000103')
on conflict do nothing;
insert into lezione_istruttore (lezione_id, persona_id)
select id, '00000000-0000-0000-0000-000000000103' from (
    (select id from lezione where corso_id = '00000000-0000-0000-0000-000000000615' order by giorno_settimana, inizio limit 4)
    union all
    (select id from lezione where corso_id = '00000000-0000-0000-0000-000000000614' order by giorno_settimana, inizio limit 2)
) t
on conflict do nothing;

-- Nel suo primo turno (in ordine di giorno e ora) metà degli iscritti ha già l'esito: si vede «da completare»
with turno as (
    select id from lezione where corso_id = '00000000-0000-0000-0000-000000000615'
    order by giorno_settimana, inizio limit 1
), stagione_ora as (
    select id from stagione where current_date between dal and al
), iscritti as (
    select i.persona_id, row_number() over (order by i.persona_id) as n
    from iscrizione i join turno t on t.id = i.lezione_id
    where i.dal <= current_date and (i.al is null or i.al >= current_date)
)
insert into esito_livello (id, stagione_id, persona_id, corso_id, esito, deciso_da, deciso_il)
select gen_random_uuid(), s.id, x.persona_id, '00000000-0000-0000-0000-000000000615',
       case when x.n % 3 = 0 then 'CONTINUA' else 'PASSA' end, '00000000-0000-0000-0000-000000000103', now()
from iscritti x cross join stagione_ora s
where x.n % 2 = 1
on conflict do nothing;
