-- Sotto controllo, uscita autonoma, certificato provvisorio (persone inventate).

-- Sofia (D0000001, carnet) è sotto controllo: entra, con un avviso alla segreteria
insert into blocco (tipo, persona_id, motivo, dal) values
    ('SORVEGLIANZA', '00000000-0000-0000-0000-000000000501', 'Controllare che non faccia entrare ospiti', current_date - 5);

-- Martina, 14 anni (D0000012): carnet a entrata libera, certificato provvisorio, permesso di uscita autonoma
insert into persona (id, nome, cognome, data_nascita) values
    ('00000000-0000-0000-0000-000000000512', 'Martina', 'Russo', (current_date - interval '14 years 3 months')::date);
insert into braccialetto (id, codice, persona_id, stato_dal, assegnato_il) values
    ('00000000-0000-0000-0000-000000000712', 'D0000012', '00000000-0000-0000-0000-000000000512', now() - interval '20 days', now() - interval '20 days');
insert into certificato_medico (persona_id, tipo, rilasciato_il, scadenza, firmato_da) values
    ('00000000-0000-0000-0000-000000000512', 'PROVVISORIO', current_date - 4, current_date + 10, 'Laura Russo (madre)');
insert into tesseramento (persona_id, stagione, dal, al, importo, pagato_il)
select '00000000-0000-0000-0000-000000000512', a || '/' || lpad(((a + 1) % 100)::text, 2, '0'), make_date(a, 9, 1), make_date(a + 1, 7, 31), 35, current_date - 4
from (select case when extract(month from current_date) >= 8 then extract(year from current_date)::int
                  else extract(year from current_date)::int - 1 end as a) s;
insert into abbonamento (persona_id, tipo, ingressi, dal, importo) values
    ('00000000-0000-0000-0000-000000000512', 'CARNET', 10, current_date - 4, 70.00);
insert into autorizzazione (persona_id, tipo, firmata_da, firmata_il, dal) values
    ('00000000-0000-0000-0000-000000000512', 'USCITA_AUTONOMA', 'Laura Russo (madre)', current_date - 4, current_date - 4);
