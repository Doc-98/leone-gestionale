-- Borsellino, phon e docce demo (persone inventate), con le regole della Leone XIII Sport dal sito: un credito vale
-- 0,20 €, ricarica minima 5 €, credito in euro valido un anno e trasferibile ai familiari, cauzione del braccialetto
-- 10 €, crediti persi se il braccialetto si smarrisce. Il phon costa un credito, la prima doccia del giorno è gratis.
-- Dispositivi con chiavi note (solo demo!): demo-phon-m, demo-phon-f, demo-phon-p, demo-docce-m, demo-docce-f, demo-saldo.
-- Ricariche (pagate) e utilizzi delle ultime settimane per un cliente su quattro; Leonardo Ferri ha 37 crediti.

update regole_borsellino set valore_credito = 0.20, ricarica_minima = 5, validita_euro_mesi = 12, trasferibile = true, restituibile = false,
    cauzione = 10, cauzione_riconsegna = 'A_SCELTA', crediti_riconsegna = true, crediti_smarrimento = 'PERSI';

insert into servizio (id, nome, crediti, gratuiti_al_giorno, minuti, ordine) values
    ('00000000-0000-0000-0000-000000000a01', 'Phon', 1, 0, 5, 1),
    ('00000000-0000-0000-0000-000000000a02', 'Doccia', 1, 1, 4, 2);

insert into dispositivo (id, nome, tipo, servizio_id, chiave_hash, creato_il) values
    ('00000000-0000-0000-0000-000000000a11', 'Phon spogliatoio maschile', 'SERVIZIO', '00000000-0000-0000-0000-000000000a01', encode(sha256(convert_to('demo-phon-m', 'UTF8')), 'hex'), now() - interval '400 days'),
    ('00000000-0000-0000-0000-000000000a12', 'Phon spogliatoio femminile', 'SERVIZIO', '00000000-0000-0000-0000-000000000a01', encode(sha256(convert_to('demo-phon-f', 'UTF8')), 'hex'), now() - interval '400 days'),
    ('00000000-0000-0000-0000-000000000a13', 'Phon spogliatoio piccoli', 'SERVIZIO', '00000000-0000-0000-0000-000000000a01', encode(sha256(convert_to('demo-phon-p', 'UTF8')), 'hex'), now() - interval '400 days'),
    ('00000000-0000-0000-0000-000000000a14', 'Docce spogliatoio maschile', 'SERVIZIO', '00000000-0000-0000-0000-000000000a02', encode(sha256(convert_to('demo-docce-m', 'UTF8')), 'hex'), now() - interval '400 days'),
    ('00000000-0000-0000-0000-000000000a15', 'Docce spogliatoio femminile', 'SERVIZIO', '00000000-0000-0000-0000-000000000a02', encode(sha256(convert_to('demo-docce-f', 'UTF8')), 'hex'), now() - interval '400 days'),
    ('00000000-0000-0000-0000-000000000a16', 'Saldo all''ingresso', 'SALDO', null, encode(sha256(convert_to('demo-saldo', 'UTF8')), 'hex'), now() - interval '400 days');

-- ── Ricariche ───────────────────────────────────────────────────────────────
create temporary table ricariche_demo as
select gen_random_uuid() as addebito, gen_random_uuid() as pagamento, p.persona,
       (array[5, 10, 20])[1 + abs(hashtext(p.persona::text || n)) % 3]::numeric(9, 2) as importo,
       current_date - (abs(hashtext(p.persona::text || 'g' || n)) % 50) - 5 as data, n
from (select distinct b.persona_id as persona from braccialetto b
      where b.stato = 'ATTIVO' and exists (select 1 from tesseramento t where t.persona_id = b.persona_id)
        and b.persona_id <> '00000000-0000-0000-0000-000000000541' and abs(hashtext(b.persona_id::text)) % 4 = 0) p
cross join generate_series(1, 2) n
where n = 1 or abs(hashtext(p.persona::text)) % 3 = 0
union all
select gen_random_uuid(), gen_random_uuid(), '00000000-0000-0000-0000-000000000541', 10, current_date - 20, 1;

insert into addebito (id, persona_id, descrizione, importo, scadenza, origine, creato_da, creato_il)
select addebito, persona, 'Ricarica di ' || (importo / 0.20)::int || ' crediti (phon e docce)', importo, data, 'RICARICA',
       '00000000-0000-0000-0000-000000000110', data::timestamptz + interval '16 hours'
from ricariche_demo;
insert into pagamento (id, pagante_id, data, importo, registrato_da, registrato_il)
select r.pagamento, coalesce((select l.tutore_id from legame l where l.persona_id = r.persona and l.al is null order by l.dal limit 1), r.persona),
       r.data, r.importo, '00000000-0000-0000-0000-000000000110', r.data::timestamptz + interval '16 hours 5 minutes'
from ricariche_demo r;
insert into pagamento_addebito (pagamento_id, addebito_id, importo) select pagamento, addebito, importo from ricariche_demo;
insert into pagamento_metodo (pagamento_id, metodo_id, importo, incassato_il)
select r.pagamento, m.id, r.importo, r.data
from ricariche_demo r
join metodo_pagamento m on m.nome = (array['Contanti', 'Contanti', 'Bancomat', 'Satispay'])[1 + abs(hashtext(r.pagamento::text)) % 4];
insert into movimento_borsellino (persona_id, saldo, quantita, causale, descrizione, pagamento_id, creato_da, creato_il)
select persona, 'CREDITI', importo / 0.20, 'RICARICA', 'Ricarica di ' || importo || ' €', pagamento, '00000000-0000-0000-0000-000000000110',
       data::timestamptz + interval '16 hours 5 minutes'
from ricariche_demo;

-- ── Utilizzi: phon (un credito) e docce (la prima del giorno gratis), dopo la prima ricarica ──
create temporary table consumatori_demo as
select persona, min(data) as dal, sum(importo / 0.20)::int as crediti, abs(hashtext(persona::text)) as h
from ricariche_demo group by persona;

create temporary table utilizzi_demo as
select gen_random_uuid() as id, c.persona, c.h, i,
       (c.dal + ((c.h + i * 37) % (current_date - c.dal + case when c.persona = '00000000-0000-0000-0000-000000000541' then 0 else 1 end))::int)::timestamp
           + time '16:30' + ((i * 13 + c.h) % 180) * interval '1 minute' as quando
from consumatori_demo c
cross join lateral generate_series(1, case when c.persona = '00000000-0000-0000-0000-000000000541' then 13
                                           else least(c.crediti - (c.h % least(c.crediti, 30)), 25) end) i;
-- niente utilizzi nel futuro (oggi solo quelli prima di adesso)
delete from utilizzi_demo where quando > (now() at time zone 'Europe/Rome')::timestamp;

insert into utilizzo (id, dispositivo_id, servizio_id, codice, persona_id, istante, esito, crediti, postazione)
select u.id, ('00000000-0000-0000-0000-000000000a1' || (1 + u.h % 3)::text)::uuid, '00000000-0000-0000-0000-000000000a01',
       (select b.codice from braccialetto b where b.persona_id = u.persona and b.stato = 'ATTIVO' order by b.assegnato_il desc limit 1),
       u.persona, u.quando at time zone 'Europe/Rome', 'OK', 1, (3 + (u.i % 3))::text
from utilizzi_demo u;
insert into movimento_borsellino (persona_id, saldo, quantita, causale, descrizione, riferimento_id, creato_il)
select u.persona, 'CREDITI', -1, 'CONSUMO',
       (array['Phon spogliatoio maschile', 'Phon spogliatoio femminile', 'Phon spogliatoio piccoli'])[1 + u.h % 3] || ' ' || (3 + (u.i % 3)),
       u.id, u.quando at time zone 'Europe/Rome'
from utilizzi_demo u;
-- una doccia gratis ogni tre phon
insert into utilizzo (dispositivo_id, servizio_id, codice, persona_id, istante, esito, crediti, postazione)
select ('00000000-0000-0000-0000-000000000a1' || (4 + u.h % 2)::text)::uuid, '00000000-0000-0000-0000-000000000a02',
       (select b.codice from braccialetto b where b.persona_id = u.persona and b.stato = 'ATTIVO' order by b.assegnato_il desc limit 1),
       u.persona, (u.quando - interval '25 minutes') at time zone 'Europe/Rome', 'GRATIS', 0, (1 + u.i % 4)::text
from utilizzi_demo u where u.i % 3 = 0;

-- ── Credito in euro ─────────────────────────────────────────────────────────
insert into movimento_borsellino (persona_id, saldo, quantita, causale, descrizione, scadenza, creato_da, creato_il) values
    ('00000000-0000-0000-0000-000000000501', 'EURO', 40, 'ACCREDITO', 'Rimborso per sospensione (malattia)', current_date + 330,
     '00000000-0000-0000-0000-000000000110', now() - interval '35 days'),
    ('00000000-0000-0000-0000-000000000508', 'EURO', 15, 'ACCREDITO', 'Rimborso per recesso (stagione scorsa)', current_date + 9,
     '00000000-0000-0000-0000-000000000110', now() - interval '356 days');

drop table utilizzi_demo;
drop table consumatori_demo;
drop table ricariche_demo;
