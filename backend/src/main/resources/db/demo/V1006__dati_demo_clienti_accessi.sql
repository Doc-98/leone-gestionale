-- Clienti e tornello (dati inventati): un caso per ogni motivo del rosso, braccialetti per tutti i clienti demo,
-- certificati, tesseramenti e abbonamenti; golden pass per lo staff.

create temporary table stagione_demo as
select make_date(a, 9, 1) as dal, make_date(a + 1, 7, 31) as al, a || '/' || lpad(((a + 1) % 100)::text, 2, '0') as nome
from (select case when extract(month from current_date) >= 8 then extract(year from current_date)::int
                  else extract(year from current_date)::int - 1 end as a) s;

-- ── Casi fissi per provare il tornello (codici da digitare o da passare) ────
insert into persona (id, nome, cognome, data_nascita) values
    ('00000000-0000-0000-0000-000000000501', 'Sofia', 'Marchetti', '1990-04-12'),
    ('00000000-0000-0000-0000-000000000502', 'Tommaso', 'Neri', '2017-06-03'),
    ('00000000-0000-0000-0000-000000000503', 'Giorgia', 'Ruggiero', '2016-02-21'),
    ('00000000-0000-0000-0000-000000000504', 'Riccardo', 'Fontanella', '1988-11-30'),
    ('00000000-0000-0000-0000-000000000505', 'Alessio', 'Morelli', '1995-01-17'),
    ('00000000-0000-0000-0000-000000000506', 'Nicolò', 'Ferrante', '1999-09-09'),
    ('00000000-0000-0000-0000-000000000507', 'Beatrice', 'Valentini', '1985-05-25'),
    ('00000000-0000-0000-0000-000000000508', 'Pietro', 'Bellini', '2014-08-14'),
    ('00000000-0000-0000-0000-000000000509', 'Emma', 'Fiore', '2016-12-01');

insert into braccialetto (id, codice, persona_id, stato, stato_dal, assegnato_il) values
    ('00000000-0000-0000-0000-000000000701', 'D0000001', '00000000-0000-0000-0000-000000000501', 'ATTIVO', now() - interval '200 days', now() - interval '200 days'),
    ('00000000-0000-0000-0000-000000000702', 'D0000002', '00000000-0000-0000-0000-000000000502', 'ATTIVO', now() - interval '40 days', now() - interval '40 days'),
    ('00000000-0000-0000-0000-000000000703', 'D0000003', '00000000-0000-0000-0000-000000000503', 'ATTIVO', now() - interval '300 days', now() - interval '300 days'),
    ('00000000-0000-0000-0000-000000000704', 'D0000004', '00000000-0000-0000-0000-000000000504', 'SMARRITO', now() - interval '6 days', now() - interval '400 days'),
    ('00000000-0000-0000-0000-000000000714', 'D0000014', '00000000-0000-0000-0000-000000000504', 'ATTIVO', now() - interval '6 days', now() - interval '6 days'),
    ('00000000-0000-0000-0000-000000000705', 'D0000005', '00000000-0000-0000-0000-000000000505', 'ATTIVO', now() - interval '90 days', now() - interval '90 days'),
    ('00000000-0000-0000-0000-000000000706', 'D0000006', '00000000-0000-0000-0000-000000000506', 'ATTIVO', now() - interval '120 days', now() - interval '120 days'),
    ('00000000-0000-0000-0000-000000000707', 'D0000007', '00000000-0000-0000-0000-000000000507', 'ATTIVO', now() - interval '150 days', now() - interval '150 days'),
    ('00000000-0000-0000-0000-000000000708', 'D0000008', '00000000-0000-0000-0000-000000000508', 'ATTIVO', now() - interval '380 days', now() - interval '380 days'),
    ('00000000-0000-0000-0000-000000000709', 'D0000009', '00000000-0000-0000-0000-000000000509', 'ATTIVO', now() - interval '20 days', now() - interval '20 days'),
    -- staff, con golden pass
    ('00000000-0000-0000-0000-000000000723', 'D0000103', '00000000-0000-0000-0000-000000000103', 'ATTIVO', now() - interval '500 days', now() - interval '500 days'),
    ('00000000-0000-0000-0000-000000000724', 'D0000104', '00000000-0000-0000-0000-000000000104', 'ATTIVO', now() - interval '500 days', now() - interval '500 days'),
    ('00000000-0000-0000-0000-000000000725', 'D0000105', '00000000-0000-0000-0000-000000000105', 'ATTIVO', now() - interval '500 days', now() - interval '500 days'),
    ('00000000-0000-0000-0000-000000000730', 'D0000110', '00000000-0000-0000-0000-000000000110', 'ATTIVO', now() - interval '500 days', now() - interval '500 days');

insert into golden_pass (persona_id, motivo, dal) values
    ('00000000-0000-0000-0000-000000000103', 'Istruttore', current_date - 500),
    ('00000000-0000-0000-0000-000000000104', 'Tornellista', current_date - 500),
    ('00000000-0000-0000-0000-000000000105', 'Istruttrice', current_date - 500),
    ('00000000-0000-0000-0000-000000000110', 'Segreteria', current_date - 500);

-- certificati: tutti validi tranne Giorgia (scaduto)
insert into certificato_medico (persona_id, tipo, rilasciato_il, scadenza)
select id, 'NON_AGONISTICO', current_date - 100, current_date + 265 from persona
where id in ('00000000-0000-0000-0000-000000000501', '00000000-0000-0000-0000-000000000502', '00000000-0000-0000-0000-000000000504',
             '00000000-0000-0000-0000-000000000505', '00000000-0000-0000-0000-000000000506', '00000000-0000-0000-0000-000000000507',
             '00000000-0000-0000-0000-000000000508', '00000000-0000-0000-0000-000000000509');
insert into certificato_medico (persona_id, tipo, rilasciato_il, scadenza) values
    ('00000000-0000-0000-0000-000000000503', 'NON_AGONISTICO', current_date - 380, current_date - 15);

-- tesseramento della stagione: tutti tranne Giorgia
insert into tesseramento (persona_id, stagione, dal, al, importo, pagato_il)
select p.id, s.nome, s.dal, s.al, 35, s.dal + 10 from persona p, stagione_demo s
where p.id in ('00000000-0000-0000-0000-000000000501', '00000000-0000-0000-0000-000000000502', '00000000-0000-0000-0000-000000000504',
               '00000000-0000-0000-0000-000000000505', '00000000-0000-0000-0000-000000000506', '00000000-0000-0000-0000-000000000507',
               '00000000-0000-0000-0000-000000000508', '00000000-0000-0000-0000-000000000509');

-- Tommaso: Delfino Giallo bisettimanale, iscritto a due turni in giorni diversi (verde solo nella fascia)
insert into iscrizione (lezione_id, persona_id, dal)
select id, '00000000-0000-0000-0000-000000000502', (select dal from stagione_demo)
from (select distinct on (giorno_settimana) id, giorno_settimana from lezione
      where corso_id = '00000000-0000-0000-0000-000000000615' order by giorno_settimana, inizio) l
order by giorno_settimana limit 2;
-- Giorgia: abbonamento a posto, ma certificato scaduto e tesseramento mancante
insert into iscrizione (lezione_id, persona_id, dal)
select id, '00000000-0000-0000-0000-000000000503', (select dal from stagione_demo)
from lezione where corso_id = '00000000-0000-0000-0000-000000000616' order by giorno_settimana, inizio limit 1;
-- Pietro: era iscritto, abbonamento scaduto
insert into iscrizione (lezione_id, persona_id, dal)
select id, '00000000-0000-0000-0000-000000000508', (select dal from stagione_demo)
from lezione where corso_id = '00000000-0000-0000-0000-000000000620' order by giorno_settimana, inizio limit 1;

insert into abbonamento (id, persona_id, tipo, corso_id, volte_a_settimana, ingressi, dal, al, importo)
select a.id::uuid, a.persona::uuid, a.tipo, a.corso::uuid, a.volte, a.ingressi, a.dal, a.al, a.importo
from stagione_demo s, lateral (values
    ('00000000-0000-0000-0000-000000000801', '00000000-0000-0000-0000-000000000501', 'CARNET', null, null, 10, current_date - 40, null::date, 70.00),
    ('00000000-0000-0000-0000-000000000802', '00000000-0000-0000-0000-000000000502', 'CORSO', '00000000-0000-0000-0000-000000000615', 2, null, s.dal, s.al, 390.00),
    ('00000000-0000-0000-0000-000000000803', '00000000-0000-0000-0000-000000000503', 'CORSO', '00000000-0000-0000-0000-000000000616', 1, null, s.dal, s.al, 230.00),
    ('00000000-0000-0000-0000-000000000804', '00000000-0000-0000-0000-000000000504', 'CARNET', null, null, 20, current_date - 60, null, 130.00),
    ('00000000-0000-0000-0000-000000000805', '00000000-0000-0000-0000-000000000505', 'CARNET', null, null, 10, current_date - 20, null, 70.00),
    ('00000000-0000-0000-0000-000000000806', '00000000-0000-0000-0000-000000000506', 'CARNET', null, null, 10, current_date - 20, null, 70.00),
    ('00000000-0000-0000-0000-000000000807', '00000000-0000-0000-0000-000000000507', 'CARNET', null, null, 10, current_date - 90, null, 70.00),
    ('00000000-0000-0000-0000-000000000808', '00000000-0000-0000-0000-000000000508', 'CORSO', '00000000-0000-0000-0000-000000000620', 1, null, s.dal, current_date - 5, 120.00)
) as a(id, persona, tipo, corso, volte, ingressi, dal, al, importo);

-- ingressi già usati: Sofia 3 su 10, Riccardo 7 su 20, Beatrice 10 su 10 (esaurito)
insert into ingresso_carnet (abbonamento_id, istante)
select '00000000-0000-0000-0000-000000000801'::uuid, now() - (g * interval '4 days') - interval '3 hours' from generate_series(1, 3) g
union all
select '00000000-0000-0000-0000-000000000804'::uuid, now() - (g * interval '5 days') - interval '2 hours' from generate_series(1, 7) g
union all
select '00000000-0000-0000-0000-000000000807'::uuid, now() - (g * interval '6 days') - interval '1 hour' from generate_series(1, 10) g;

insert into blocco (persona_id, braccialetto_id, motivo, dal, al) values
    (null, '00000000-0000-0000-0000-000000000705', 'Braccialetto prestato a un''altra persona', current_date - 3, null),
    ('00000000-0000-0000-0000-000000000506', null, 'Comportamento scorretto in vasca', current_date - 2, current_date + 28);

-- ── Tutti gli altri clienti demo ────────────────────────────────────────────
create temporary table clienti_demo as
select p.id, p.data_nascita, abs(hashtext(p.id::text)) as h,
       extract(year from age(current_date, p.data_nascita)) as eta,
       exists (select 1 from iscrizione i where i.persona_id = p.id) as iscritto
from persona p
where p.data_nascita is not null
  and not exists (select 1 from account a where a.persona_id = p.id)
  and p.id::text not like '00000000-0000-0000-0000-0000000005%';

insert into braccialetto (codice, persona_id, assegnato_il, stato_dal)
select upper(substr(md5(id::text), 1, 8)), id, now() - (h % 700) * interval '1 day', now() - (h % 700) * interval '1 day'
from clienti_demo where iscritto or eta >= 18;

-- certificati: uno su venti scaduto
insert into certificato_medico (persona_id, tipo, rilasciato_il, scadenza)
select id, case when eta >= 14 and h % 7 = 0 then 'AGONISTICO' else 'NON_AGONISTICO' end,
       case when h % 20 = 0 then current_date - (h % 45) - 366 else current_date - (h % 300) - 30 end,
       case when h % 20 = 0 then current_date - (h % 45) - 1 else current_date + (h % 330) + 5 end
from clienti_demo where iscritto or eta >= 18;

-- tesseramento: uno su venticinque non l'ha pagato
insert into tesseramento (persona_id, stagione, dal, al, importo, pagato_il)
select c.id, s.nome, s.dal, s.al, 35, s.dal + (c.h % 40)
from clienti_demo c, stagione_demo s where (c.iscritto or c.eta >= 18) and c.h % 25 <> 1;

-- abbonamento per ogni corso frequentato, con le volte a settimana dei turni; uno su trenta scaduto
insert into abbonamento (persona_id, tipo, corso_id, volte_a_settimana, dal, al)
select i.persona_id, 'CORSO', l.corso_id, least(count(*), 7), s.dal,
       case when max(c.h) % 30 = 2 then current_date - (max(c.h) % 20) - 1 else s.al end
from iscrizione i
join lezione l on l.id = i.lezione_id
join clienti_demo c on c.id = i.persona_id
cross join stagione_demo s
group by i.persona_id, l.corso_id, s.dal, s.al;

-- adulti senza corso: carnet da 10 o 20 ingressi, in parte usati
create temporary table carnet_demo as
select gen_random_uuid() as id, c.id as persona, c.h, case when c.h % 3 = 0 then 20 else 10 end as ingressi
from clienti_demo c where not c.iscritto and c.eta >= 18;
insert into abbonamento (id, persona_id, tipo, ingressi, dal)
select id, persona, 'CARNET', ingressi, current_date - (h % 80) - 1 from carnet_demo;
insert into ingresso_carnet (abbonamento_id, istante)
select k.id, now() - (g * interval '3 days') - (k.h % 600) * interval '1 minute'
from carnet_demo k join lateral generate_series(1, least(k.h % 12, k.ingressi)) g on true;

drop table carnet_demo;
drop table clienti_demo;
drop table stagione_demo;
