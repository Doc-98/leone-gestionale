-- Chiusure di cassa demo (ADR 0018, parte 11): le ultime due settimane, una al giorno (la domenica la segreteria è
-- chiusa), aperte alle 8:30 e chiuse alle 21:30 dalla segreteria, con il fondo di 100 € riportato. Quasi sempre la
-- cassa torna; qualche giorno mancano 2 € o ce ne sono 50 centesimi in più. Oggi la cassa è ancora da aprire.
do $$
declare
    cassa   uuid := '00000000-0000-0000-0000-00000000ca01';
    segr    uuid := (select persona_id from account where email = 'segreteria@demo.leone');
    giorno  date;
    dal     timestamptz := (current_date - 14)::timestamp at time zone 'Europe/Rome';
    fine    timestamptz;
    entrati numeric;
    usciti  numeric;
    quanti  int;
    per     jsonb;
    attesi  numeric;
    diff    numeric;
    contati numeric;
begin
    for giorno in select g::date from generate_series(current_date - 14, current_date - 1, interval '1 day') g loop
        continue when extract(isodow from giorno) = 7;
        fine := (giorno + time '21:30') at time zone 'Europe/Rome';
        select coalesce(sum(pm.importo) filter (where mp.contanti), 0), count(distinct p.id) into entrati, quanti
        from pagamento p join pagamento_metodo pm on pm.pagamento_id = p.id join metodo_pagamento mp on mp.id = pm.metodo_id
        where p.annullato_il is null and p.registrato_il > dal and p.registrato_il <= fine;
        select coalesce(sum(u.importo), 0) into usciti
        from uscita u join metodo_pagamento mp on mp.id = u.metodo_id
        where mp.contanti and u.annullato_il is null and u.registrato_il > dal and u.registrato_il <= fine;
        select coalesce(jsonb_agg(jsonb_build_object('metodo', x.nome, 'contanti', x.contanti, 'entrate', x.entrate, 'uscite', 0) order by x.ordine), '[]'::jsonb)
        into per
        from (select mp.nome, mp.contanti, mp.ordine, sum(pm.importo) as entrate
              from pagamento p join pagamento_metodo pm on pm.pagamento_id = p.id join metodo_pagamento mp on mp.id = pm.metodo_id
              where p.annullato_il is null and p.registrato_il > dal and p.registrato_il <= fine
              group by mp.nome, mp.contanti, mp.ordine) x;
        attesi := 100 + entrati - usciti;
        diff := case extract(day from giorno)::int % 7 when 2 then -2.00 when 5 then 0.50 else 0.00 end;
        contati := attesi + diff;
        insert into sessione_cassa (id, cassa_id, dal, aperta_il, aperta_da, esplicita, fondo, chiusa_il, chiusa_da, attesi, contati, differenza,
                                    lasciato, versato, note, riepilogo)
        values (gen_random_uuid(), cassa, dal, (giorno + time '08:30') at time zone 'Europe/Rome', segr, true, 100, fine, segr, attesi, contati, diff,
                least(100, contati), contati - least(100, contati), case when diff < 0 then 'Forse un resto sbagliato' end,
                jsonb_build_object('perMetodo', per, 'pagamenti', quanti, 'entrati', entrati, 'usciti', usciti, 'versamenti', 0, 'spese', 0, 'aggiunte', 0));
        dal := fine;
    end loop;
end $$;
