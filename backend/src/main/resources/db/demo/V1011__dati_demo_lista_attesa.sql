-- Liste d'attesa (dati inventati): due turni pieni adatti a un bambino di 7 anni; nel secondo qualcuno si ritira tra
-- 6 giorni, quindi si libera un posto. Sara Neri (sorella di Tommaso) è la prima del primo turno.
create temporary table pieni_demo as
select l.id, row_number() over (order by c.nome, l.giorno_settimana, l.inizio) as k
from lezione l
join corso c on c.id = l.corso_id
where l.posti is not null and c.attivo and 7 between coalesce(c.eta_da, 0) and coalesce(c.eta_a, 120)
  and (select count(*) from iscrizione i where i.lezione_id = l.id and i.dal <= current_date and (i.al is null or i.al >= current_date)) >= l.posti;

insert into persona (id, nome, cognome, data_nascita, email) values
    ('00000000-0000-0000-0000-000000000531', 'Elena', 'Fumagalli', current_date - interval '7 years 2 months', 'fumagalli.famiglia@example.org'),
    ('00000000-0000-0000-0000-000000000532', 'Pietro', 'Galbiati', current_date - interval '6 years 9 months', 'galbiati.famiglia@example.org'),
    ('00000000-0000-0000-0000-000000000533', 'Irene', 'Bonfanti', current_date - interval '7 years 5 months', null),
    ('00000000-0000-0000-0000-000000000534', 'Matteo', 'Ronchi', current_date - interval '7 years 1 month', 'ronchi.famiglia@example.org');

insert into attesa (lezione_id, persona_id, aggiunta_il, note, avvisata_il)
select p.id, v.persona::uuid, now() - v.giorni * interval '1 day', v.note, v.avvisata
from pieni_demo p
join (values
    (1, '00000000-0000-0000-0000-000000000522', 20, null, null::timestamptz),
    (1, '00000000-0000-0000-0000-000000000531', 12, 'Va bene anche un altro giorno', null),
    (1, '00000000-0000-0000-0000-000000000533', 3, null, null),
    (2, '00000000-0000-0000-0000-000000000532', 30, null, now() - interval '1 day'),
    (2, '00000000-0000-0000-0000-000000000534', 9, 'Ha un fratello nel turno dopo', null)
) as v (k, persona, giorni, note, avvisata) on v.k = p.k;

-- nel secondo turno un iscritto si ritira tra 6 giorni
update iscrizione set al = current_date + 6
where id = (select i.id from iscrizione i join pieni_demo p on p.id = i.lezione_id and p.k = 2
            where i.al is null order by i.id limit 1);

drop table pieni_demo;
