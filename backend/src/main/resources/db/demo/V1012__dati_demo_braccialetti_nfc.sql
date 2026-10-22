-- Braccialetti nel formato dei chip NFC (seriali di 4 e 7 byte), per le prove di lettura con il telefono e con il
-- telefono come lettore del PC. Persone inventate, con casi diversi: verdi (carnet a entrata libera, validi a ogni ora)
-- e rossi (tesseramento mancante, nessun abbonamento).
create temporary table stagione_nfc as
select make_date(a, 9, 1) as dal, make_date(a + 1, 7, 31) as al, a || '/' || lpad(((a + 1) % 100)::text, 2, '0') as nome
from (select case when extract(month from current_date) >= 8 then extract(year from current_date)::int
                  else extract(year from current_date)::int - 1 end as a) s;

insert into persona (id, nome, cognome, data_nascita, sesso, email) values
    ('00000000-0000-0000-0000-000000000541', 'Leonardo', 'Ferri', '1997-05-14', 'M', 'leonardo.ferri@example.org'),
    ('00000000-0000-0000-0000-000000000542', 'Chiara', 'Venturi', '1992-11-03', 'F', 'chiara.venturi@example.org'),
    ('00000000-0000-0000-0000-000000000543', 'Davide', 'Colombi', '1985-02-27', 'M', 'davide.colombi@example.org'),
    ('00000000-0000-0000-0000-000000000544', 'Elisa', 'Marchi', '2001-07-19', 'F', 'elisa.marchi@example.org'),
    ('00000000-0000-0000-0000-000000000545', 'Federico', 'Bernardi', '1979-09-08', 'M', null),
    ('00000000-0000-0000-0000-000000000546', 'Giada', 'Rinaldi', '1995-04-22', 'F', 'giada.rinaldi@example.org');

insert into braccialetto (codice, persona_id, stato, stato_dal, assegnato_il) values
    ('0C7A5CAE', '00000000-0000-0000-0000-000000000541', 'ATTIVO', now() - interval '60 days', now() - interval '60 days'),
    ('B4E21A07', '00000000-0000-0000-0000-000000000542', 'ATTIVO', now() - interval '120 days', now() - interval '120 days'),
    ('5D03C8F1', '00000000-0000-0000-0000-000000000543', 'ATTIVO', now() - interval '30 days', now() - interval '30 days'),
    ('04A7C31B5E6F80', '00000000-0000-0000-0000-000000000544', 'ATTIVO', now() - interval '15 days', now() - interval '15 days'),
    ('E91F4D62', '00000000-0000-0000-0000-000000000545', 'ATTIVO', now() - interval '200 days', now() - interval '200 days'),
    ('04D2569AB33C80', '00000000-0000-0000-0000-000000000546', 'ATTIVO', now() - interval '90 days', now() - interval '90 days');

-- certificati validi (Davide scade tra 5 giorni: compare nelle scadenze)
insert into certificato_medico (persona_id, tipo, rilasciato_il, scadenza) values
    ('00000000-0000-0000-0000-000000000541', 'NON_AGONISTICO', current_date - 60, current_date + 305),
    ('00000000-0000-0000-0000-000000000542', 'NON_AGONISTICO', current_date - 200, current_date + 165),
    ('00000000-0000-0000-0000-000000000543', 'NON_AGONISTICO', current_date - 360, current_date + 5),
    ('00000000-0000-0000-0000-000000000544', 'AGONISTICO', current_date - 15, current_date + 350),
    ('00000000-0000-0000-0000-000000000545', 'NON_AGONISTICO', current_date - 100, current_date + 265),
    ('00000000-0000-0000-0000-000000000546', 'NON_AGONISTICO', current_date - 90, current_date + 275);

-- tesseramento della stagione: tutti tranne Elisa
insert into tesseramento (persona_id, stagione, dal, al, importo, pagato_il)
select p.id, s.nome, s.dal, s.al, 35, s.dal + 5 from persona p, stagione_nfc s
where p.id in ('00000000-0000-0000-0000-000000000541', '00000000-0000-0000-0000-000000000542', '00000000-0000-0000-0000-000000000543',
               '00000000-0000-0000-0000-000000000545', '00000000-0000-0000-0000-000000000546');

-- carnet a entrata libera (verdi a ogni ora): tutti tranne Federico, che non ha abbonamenti
insert into abbonamento (persona_id, tipo, ingressi, dal, al, importo) values
    ('00000000-0000-0000-0000-000000000541', 'CARNET', 100, current_date - 60, null, 600.00),
    ('00000000-0000-0000-0000-000000000542', 'CARNET', 10, current_date - 20, null, 70.00),
    ('00000000-0000-0000-0000-000000000543', 'CARNET', 20, current_date - 30, null, 130.00),
    ('00000000-0000-0000-0000-000000000544', 'CARNET', 10, current_date - 15, null, 70.00),
    ('00000000-0000-0000-0000-000000000546', 'CARNET', 50, current_date - 90, null, 300.00);

drop table stagione_nfc;
