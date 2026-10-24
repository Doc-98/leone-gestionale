-- Addebiti e pagamenti demo della stagione in corso (persone inventate), come se fossero stati registrati dalla
-- segreteria: quota associativa, abbonamenti ai corsi in due rate (all'iscrizione e entro il 14 settembre, come la
-- regola demo), carnet. Quasi tutti hanno pagato; uno su dieci non ha pagato la seconda rata (scaduta), qualcuno non ha
-- pagato niente. I minori li pagano i genitori, quando sono registrati. Metodi misti: contanti, bancomat, carta,
-- Satispay, bonifico (gli ultimi bonifici ancora da incassare), qualche pagamento metà in contanti e metà col bancomat.
-- Tommaso Neri (D0000002): la mamma ha pagato la prima rata, la seconda è scaduta.

create temporary table stagione_corrente as
select id, nome, dal, al, make_date(extract(year from dal)::int, 9, 14) as seconda_rata from stagione where current_date between dal and al;

-- ── Importo e offerta degli abbonamenti della stagione, dal listino ─────────
create temporary table prezzi_demo as
select oc.corso_id, o.id as offerta, p.volte, p.importo
from offerta o
join offerta_corso oc on oc.offerta_id = o.id
join offerta_prezzo p on p.offerta_id = o.id and p.gruppo_id is null
where o.stagione_id = (select id from stagione_corrente);

update abbonamento a
set offerta_id = coalesce(a.offerta_id, x.offerta),
    importo_listino = case when a.importo is null then x.importo else a.importo_listino end,
    importo = coalesce(a.importo, x.importo)
from abbonamento b
cross join lateral (
    select pr.offerta, pr.importo from prezzi_demo pr
    where pr.corso_id = b.corso_id
    order by (pr.volte = case when b.volte_a_settimana >= 7 then 0 else b.volte_a_settimana end) desc nulls last, (pr.volte is null) desc, pr.importo
    limit 1
) x
where a.id = b.id and b.tipo = 'CORSO' and b.annullato_il is null and b.dal >= (select dal from stagione_corrente);

update abbonamento set importo = case when ingressi >= 20 then 240 when ingressi >= 10 then 130 else 70 end
where tipo = 'CARNET' and importo is null and annullato_il is null;

-- ── Addebiti ────────────────────────────────────────────────────────────────
create temporary table addebiti_demo (id uuid, persona uuid, descrizione text, importo numeric(9, 2), scadenza date, origine text, rif uuid,
                                      offerta uuid, rata int, rate int, gruppo text);

-- quota associativa
insert into addebiti_demo
select gen_random_uuid(), t.persona_id, 'Quota associativa ' || t.stagione, t.importo, t.dal, 'TESSERAMENTO', t.id, null, null, null, 'A'
from tesseramento t where t.annullato_il is null and t.importo > 0 and t.stagione = (select nome from stagione_corrente);

-- abbonamenti ai corsi: due rate se iniziano prima della scadenza della seconda, altrimenti in un'unica soluzione
create temporary table corsi_demo as
select b.id, b.persona_id, b.importo, b.dal, b.offerta_id,
       c.nome || ' · ' || case when b.volte_a_settimana >= 7 then 'open' when b.volte_a_settimana = 1 then '1 volta a settimana'
                               else b.volte_a_settimana || ' volte a settimana' end as descrizione
from abbonamento b join corso c on c.id = b.corso_id
where b.tipo = 'CORSO' and b.annullato_il is null and b.importo > 0 and b.dal >= (select dal from stagione_corrente);

insert into addebiti_demo
select gen_random_uuid(), b.persona_id, b.descrizione || ' · rata 1 di 2', round(b.importo / 2, 2), b.dal, 'ABBONAMENTO', b.id, b.offerta_id, 1, 2, 'A'
from corsi_demo b, stagione_corrente s where b.dal < s.seconda_rata;
insert into addebiti_demo
select gen_random_uuid(), b.persona_id, b.descrizione || ' · rata 2 di 2', b.importo - round(b.importo / 2, 2), s.seconda_rata, 'ABBONAMENTO', b.id,
       b.offerta_id, 2, 2, 'B'
from corsi_demo b, stagione_corrente s where b.dal < s.seconda_rata;
insert into addebiti_demo
select gen_random_uuid(), b.persona_id, b.descrizione, b.importo, b.dal, 'ABBONAMENTO', b.id, b.offerta_id, null, null, 'A'
from corsi_demo b, stagione_corrente s where b.dal >= s.seconda_rata;

-- carnet (si pagano il giorno in cui si comprano)
insert into addebiti_demo
select gen_random_uuid(), b.persona_id, 'Carnet da ' || b.ingressi || ' ingressi', b.importo, b.dal, 'ABBONAMENTO', b.id, b.offerta_id, null, null, 'C' || b.id
from abbonamento b where b.tipo = 'CARNET' and b.annullato_il is null and b.importo > 0 and b.dal <= current_date;

-- oggi: qualche ingresso extra al nuoto libero
insert into addebiti_demo
select gen_random_uuid(), p.id, 'Ingresso extra', 5, current_date, 'ALTRO', null, null, null, null, 'X' || p.id
from (select x.id from (select distinct b.persona_id as id from abbonamento b where b.tipo = 'CARNET' and b.annullato_il is null) x
      order by md5(x.id::text) limit 4) p;

insert into addebito (id, persona_id, descrizione, importo, scadenza, origine, riferimento_id, offerta_id, rata, rate, creato_da, creato_il)
select id, persona, descrizione, importo, scadenza, origine, rif, offerta, rata, rate, '00000000-0000-0000-0000-000000000110',
       least(scadenza, current_date)::timestamptz + interval '10 hours'
from addebiti_demo;

-- ── Cosa è stato pagato ─────────────────────────────────────────────────────
create temporary table pagati_demo as
select a.*, abs(hashtext(a.persona::text)) as h, t.pagato_il
from addebiti_demo a left join tesseramento t on a.origine = 'TESSERAMENTO' and t.id = a.rif;

delete from pagati_demo
where (origine = 'TESSERAMENTO' and pagato_il is null)                                   -- quota non pagata (come il tesseramento)
   or (gruppo in ('A', 'B') and origine = 'ABBONAMENTO' and h % 50 = 7)                  -- non ha pagato niente
   or (gruppo = 'B' and (h % 10 = 3 or persona = '00000000-0000-0000-0000-000000000502')); -- seconda rata scaduta

-- un pagamento per persona e momento: all'iscrizione (quota e prima rata), la seconda rata, ogni carnet, gli extra di oggi
create temporary table pagamenti_demo as
select md5(p.persona::text || p.gruppo)::uuid as id, p.persona, p.gruppo, sum(p.importo) as importo,
       least(current_date, case
           when p.gruppo = 'A' then coalesce(max(p.pagato_il), (select dal from stagione_corrente) + (max(p.h) % 10)::int)
           when p.gruppo = 'B' then (select seconda_rata from stagione_corrente) - (max(p.h) % 6)::int
           else max(p.scadenza) end) as data,
       coalesce((select l.tutore_id from legame l where l.persona_id = p.persona and l.al is null order by l.dal limit 1), p.persona) as pagante
from pagati_demo p
group by p.persona, p.gruppo;

insert into pagamento (id, pagante_id, data, importo, registrato_da, registrato_il)
select id, pagante, data, importo, '00000000-0000-0000-0000-000000000110', data::timestamptz + interval '17 hours' + (abs(hashtext(id::text)) % 120) * interval '1 minute'
from pagamenti_demo;

insert into pagamento_addebito (pagamento_id, addebito_id, importo)
select md5(p.persona::text || p.gruppo)::uuid, p.id, p.importo from pagati_demo p;

-- metodi: contanti, bancomat, carta, Satispay, bonifico; ogni tredici un pagamento misto (50 € in contanti, il resto col bancomat)
create temporary table metodi_demo as
select p.id as pagamento, p.importo, p.data, abs(hashtext(p.id::text)) % 20 as m, abs(hashtext(p.id::text)) % 13 = 0 and p.importo > 60 as misto
from pagamenti_demo p;

insert into pagamento_metodo (pagamento_id, metodo_id, importo, incassato_il, riferimento)
select d.pagamento, mp.id, case when d.misto then d.importo - 50 else d.importo end,
       case when not mp.da_incassare then d.data
            when d.data + 3 < current_date and abs(hashtext(d.pagamento::text)) % 7 <> 0 then d.data + 3 end,
       case when mp.da_incassare then 'CRO ' || lpad((abs(hashtext(d.pagamento::text)) % 1000000)::text, 6, '0') end
from metodi_demo d
join metodo_pagamento mp on mp.nome = case
    when d.misto then 'Bancomat'
    when d.m < 6 then 'Contanti' when d.m < 12 then 'Bancomat' when d.m < 15 then 'Carta di credito' when d.m < 17 then 'Satispay'
    else 'Bonifico' end;
insert into pagamento_metodo (pagamento_id, metodo_id, importo, incassato_il)
select d.pagamento, (select id from metodo_pagamento where nome = 'Contanti'), 50, d.data from metodi_demo d where d.misto;

drop table metodi_demo;
drop table pagamenti_demo;
drop table pagati_demo;
drop table corsi_demo;
drop table addebiti_demo;
drop table prezzi_demo;
drop table stagione_corrente;
