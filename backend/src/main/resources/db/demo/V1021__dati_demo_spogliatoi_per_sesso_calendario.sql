-- Spogliatoi riservati per sesso e il sesso dello staff demo (persone inventate), più qualche giorno del calendario
-- della stagione in corso: una chiusura, un giorno a orario ridotto e la chiusura estiva.

update postazione set solo_sesso = 'M' where id in ('00000000-0000-0000-0000-000000000401', '00000000-0000-0000-0000-000000000402',
                                                   '00000000-0000-0000-0000-000000000406');
update postazione set solo_sesso = 'F' where id in ('00000000-0000-0000-0000-000000000403', '00000000-0000-0000-0000-000000000407');

update persona set sesso = 'M' where id in ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000103');
update persona set sesso = 'F' where id in ('00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-000000000105');
-- staff degli spogliatoi (V1016): 1–10 e 26 uomini, 11–25 donne
update persona set sesso = case when n between 11 and 25 then 'F' else 'M' end
from generate_series(1, 26) n
where persona.id = ('00000000-0000-0000-0000-00000000' || (1300 + n)::text)::uuid;

-- Calendario della stagione in corso (dal 1/9): chiusure di tutto l'impianto, cioè senza spazi
insert into evento (impianto_id, tipo, titolo, data, data_fine, inizio, fine, note)
select '00000000-0000-0000-0000-0000000000b1', 'CHIUSURA', titolo, make_date(a + dy, mese, dal), case when al is null then null else make_date(a + dy, mese, al) end,
       inizio::time, fine::time, null
from (select case when extract(month from current_date) >= 9 then extract(year from current_date)::int
                  else extract(year from current_date)::int - 1 end as a) s,
     (values ('Immacolata', 0, 12, 8, null, '00:00', '23:59'),
             ('Festa di Natale della società', 0, 12, 19, null, '18:00', '23:59'),
             ('Chiusura estiva', 1, 8, 1, 31, '00:00', '23:59')) v(titolo, dy, mese, dal, al, inizio, fine);

-- I turni demo già generati nei giorni di chiusura totale non avrebbero senso: via (con le eventuali richieste)
create temporary table turni_chiusi as
select t.id from turno t
join evento e on e.tipo = 'CHIUSURA' and not e.annullato and e.inizio = '00:00' and e.fine >= '23:59'
              and t.data between e.data and coalesce(e.data_fine, e.data)
              and not exists (select 1 from evento_spazio es where es.evento_id = e.id);
delete from richiesta_turno where turno_id in (select id from turni_chiusi) or turno_collega_id in (select id from turni_chiusi);
delete from turno where id in (select id from turni_chiusi);
drop table turni_chiusi;
