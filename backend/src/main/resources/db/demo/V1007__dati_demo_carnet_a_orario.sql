-- Un carnet legato agli orari del nuoto libero (persona inventata): verde solo nella fascia del nuoto libero.
insert into persona (id, nome, cognome, data_nascita) values
    ('00000000-0000-0000-0000-000000000510', 'Marco', 'Gentile', '1979-03-08');
insert into braccialetto (id, codice, persona_id, stato_dal, assegnato_il) values
    ('00000000-0000-0000-0000-000000000710', 'D0000010', '00000000-0000-0000-0000-000000000510', now() - interval '30 days', now() - interval '30 days');
insert into certificato_medico (persona_id, tipo, rilasciato_il, scadenza) values
    ('00000000-0000-0000-0000-000000000510', 'NON_AGONISTICO', current_date - 60, current_date + 300);
insert into tesseramento (persona_id, stagione, dal, al, importo, pagato_il)
select '00000000-0000-0000-0000-000000000510', a || '/' || lpad(((a + 1) % 100)::text, 2, '0'), make_date(a, 9, 1), make_date(a + 1, 7, 31), 35, make_date(a, 9, 15)
from (select case when extract(month from current_date) >= 8 then extract(year from current_date)::int
                  else extract(year from current_date)::int - 1 end as a) s;
insert into abbonamento (id, persona_id, tipo, corso_id, ingressi, dal, importo) values
    ('00000000-0000-0000-0000-000000000810', '00000000-0000-0000-0000-000000000510', 'CARNET', '00000000-0000-0000-0000-000000000625', 10, current_date - 30, 60.00);
