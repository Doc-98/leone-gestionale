-- Liste d'attesa più ricche (persone inventate, già nei dati demo): alcuni turni pieni con 2-5 persone in attesa,
-- un posto libero da oggi in AquaFitness, un avviso senza risposta, una conferma, e lo storico che ne deriva.
do $$
declare
    segr uuid := (select persona_id from account where email = 'segreteria@demo.leone');
    t record;
    p record;
    k int;
    l uuid;
begin
    for t in
        select * from (values
            ('Delfino Giallo', 2, time '17:00', 4),
            ('Delfino Rosso', 5, time '16:10', 3),
            ('Delfino Nero', 4, time '17:50', 2),
            ('Ambientamento vasca piccola · 4 anni', 6, time '11:50', 5),
            ('AquaFitness', 1, time '13:25', 3),
            ('Adulti · intermedio', 1, time '08:50', 2)
        ) as v (corso, giorno, inizio, quanti)
    loop
        select x.id into l from lezione x join corso c on c.id = x.corso_id
        where c.nome = t.corso and x.giorno_settimana = t.giorno and x.inizio = t.inizio limit 1;
        continue when l is null;
        k := 0;
        for p in
            select pe.id from persona pe, lezione x join corso c on c.id = x.corso_id
            where x.id = l and pe.data_nascita is not null
              and date_part('year', age(pe.data_nascita)) between coalesce(c.eta_da, 0) and coalesce(c.eta_a, 120)
              and not exists (select 1 from iscrizione i where i.lezione_id = l and i.persona_id = pe.id)
              and not exists (select 1 from attesa a where a.persona_id = pe.id and a.stato = 'IN_ATTESA')
            order by hashtext(pe.id::text || t.corso)
            limit t.quanti
        loop
            k := k + 1;
            insert into attesa (id, lezione_id, persona_id, aggiunta_il, aggiunta_da, stato)
            values (gen_random_uuid(), l, p.id, now() - ((75 - k * 12 - t.giorno) * interval '1 day'), segr, 'IN_ATTESA');
        end loop;
    end loop;

    -- chi aspetta il Delfino Rosso e ha 8 anni aspetta anche il Delfino Azzurro del lunedì alle 17
    insert into attesa (id, lezione_id, persona_id, aggiunta_il, aggiunta_da, stato)
    select gen_random_uuid(), (select x.id from lezione x join corso c on c.id = x.corso_id
                               where c.nome = 'Delfino Azzurro' and x.giorno_settimana = 1 and x.inizio = '17:00' limit 1),
           a.persona_id, a.aggiunta_il + interval '2 days', segr, 'IN_ATTESA'
    from attesa a join lezione x on x.id = a.lezione_id join corso c on c.id = x.corso_id join persona pe on pe.id = a.persona_id
    where c.nome = 'Delfino Rosso' and a.stato = 'IN_ATTESA' and date_part('year', age(pe.data_nascita)) = 8
    order by a.aggiunta_il limit 1;

    -- AquaFitness del lunedì: un'iscritta ha smesso ieri, c'è un posto da oggi
    update iscrizione set al = current_date - 1
    where id = (select i.id from iscrizione i join lezione x on x.id = i.lezione_id join corso c on c.id = x.corso_id
                where c.nome = 'AquaFitness' and x.giorno_settimana = 1 and x.inizio = '13:25' and i.al is null order by i.id limit 1);

    -- Delfino Giallo: la prima è stata avvisata 6 giorni fa e non ha risposto entro i 3 giorni
    update attesa set avvisata_il = now() - interval '6 days', risposta_entro = current_date - 3
    where id = (select a.id from attesa a join lezione x on x.id = a.lezione_id join corso c on c.id = x.corso_id
                where c.nome = 'Delfino Giallo' and x.giorno_settimana = 2 and x.inizio = '17:00' and a.stato = 'IN_ATTESA'
                order by a.aggiunta_il limit 1);
    -- e nel suo turno un iscritto si ritira tra 4 giorni (così il posto c'è)
    update iscrizione set al = current_date + 4
    where id = (select i.id from iscrizione i join lezione x on x.id = i.lezione_id join corso c on c.id = x.corso_id
                where c.nome = 'Delfino Giallo' and x.giorno_settimana = 2 and x.inizio = '17:00' and i.al is null order by i.id limit 1);

    -- chi era già stato avvisato ieri ha tempo fino a dopodomani
    update attesa set risposta_entro = avvisata_il::date + 3 where avvisata_il is not null and risposta_entro is null;

    -- Adulti intermedio: al secondo è stato chiesto se aspetta ancora, e ha confermato
    update attesa set conferma_chiesta_il = now() - interval '12 days', confermata_il = now() - interval '10 days'
    where id = (select a.id from attesa a join lezione x on x.id = a.lezione_id join corso c on c.id = x.corso_id
                where c.nome = 'Adulti · intermedio' and a.stato = 'IN_ATTESA' order by a.aggiunta_il offset 1 limit 1);

    -- lo storico
    insert into attesa_evento (id, attesa_id, tipo, quando, da)
    select gen_random_uuid(), a.id, 'AGGIUNTA', a.aggiunta_il, coalesce(a.aggiunta_da, segr) from attesa a;
    insert into attesa_evento (id, attesa_id, tipo, quando, da)
    select gen_random_uuid(), a.id, 'AVVISATA', a.avvisata_il, segr from attesa a where a.avvisata_il is not null;
    insert into attesa_evento (id, attesa_id, tipo, quando, da)
    select gen_random_uuid(), a.id, 'CONFERMA_CHIESTA', a.conferma_chiesta_il, segr from attesa a where a.conferma_chiesta_il is not null;
    insert into attesa_evento (id, attesa_id, tipo, quando, da)
    select gen_random_uuid(), a.id, 'CONFERMATA', a.confermata_il, segr from attesa a where a.confermata_il is not null;
    insert into attesa_evento (id, attesa_id, tipo, quando, da)
    select gen_random_uuid(), a.id, case a.stato when 'ISCRITTA' then 'ISCRITTA' else 'RINUNCIA' end, a.chiusa_il, segr
    from attesa a where a.stato <> 'IN_ATTESA' and a.chiusa_il is not null;
end $$;
