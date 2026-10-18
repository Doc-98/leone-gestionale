-- Dati demo del piano vasca: corsie, corsi con lezioni settimanali, qualche evento.
-- Nessuna persona reale: gli istruttori sono le persone demo.

-- Sei corsie nella vasca grande
insert into spazio (id, impianto_id, nome, tipo, ordine, padre_id)
select ('00000000-0000-0000-0000-0000000001' || lpad(n::text, 2, '0'))::uuid, '00000000-0000-0000-0000-0000000000b1',
       'Corsia ' || n, 'CORSIA', 10 + n, '00000000-0000-0000-0000-0000000000c1'
from generate_series(1, 6) as n;

insert into corso (id, impianto_id, nome, tipo, livello, eta_da, eta_a, istruttore_id) values
    ('00000000-0000-0000-0000-000000000601', '00000000-0000-0000-0000-0000000000b1', 'Acquaticità 3-5 anni', 'ACQUATICITA', null, 3, 5, '00000000-0000-0000-0000-000000000104'),
    ('00000000-0000-0000-0000-000000000602', '00000000-0000-0000-0000-0000000000b1', 'Scuola nuoto ragazzi · delfini', 'SCUOLA_NUOTO', 'Delfini', 8, 12, '00000000-0000-0000-0000-000000000103'),
    ('00000000-0000-0000-0000-000000000603', '00000000-0000-0000-0000-0000000000b1', 'Scuola nuoto ragazzi · pinguini', 'SCUOLA_NUOTO', 'Pinguini', 6, 9, '00000000-0000-0000-0000-000000000105'),
    ('00000000-0000-0000-0000-000000000604', '00000000-0000-0000-0000-0000000000b1', 'Agonismo esordienti', 'AGONISMO', 'Esordienti A', 10, 13, '00000000-0000-0000-0000-000000000102'),
    ('00000000-0000-0000-0000-000000000605', '00000000-0000-0000-0000-0000000000b1', 'Acquagym', 'ACQUAGYM', null, 18, null, '00000000-0000-0000-0000-000000000105'),
    ('00000000-0000-0000-0000-000000000606', '00000000-0000-0000-0000-0000000000b1', 'Nuoto libero', 'NUOTO_LIBERO', null, null, null, null);

-- Lezioni (valide dall'inizio dell'anno, senza scadenza)
insert into lezione (id, corso_id, giorno_settimana, inizio, fine, valida_dal) values
    ('00000000-0000-0000-0000-000000000701', '00000000-0000-0000-0000-000000000601', 2, '17:00', '17:45', date_trunc('year', current_date)::date),
    ('00000000-0000-0000-0000-000000000702', '00000000-0000-0000-0000-000000000601', 4, '17:00', '17:45', date_trunc('year', current_date)::date),
    ('00000000-0000-0000-0000-000000000703', '00000000-0000-0000-0000-000000000602', 1, '17:00', '18:00', date_trunc('year', current_date)::date),
    ('00000000-0000-0000-0000-000000000704', '00000000-0000-0000-0000-000000000602', 3, '17:00', '18:00', date_trunc('year', current_date)::date),
    ('00000000-0000-0000-0000-000000000705', '00000000-0000-0000-0000-000000000603', 1, '18:00', '19:00', date_trunc('year', current_date)::date),
    ('00000000-0000-0000-0000-000000000706', '00000000-0000-0000-0000-000000000603', 5, '17:30', '18:30', date_trunc('year', current_date)::date),
    ('00000000-0000-0000-0000-000000000707', '00000000-0000-0000-0000-000000000604', 2, '18:00', '19:30', date_trunc('year', current_date)::date),
    ('00000000-0000-0000-0000-000000000708', '00000000-0000-0000-0000-000000000604', 4, '18:00', '19:30', date_trunc('year', current_date)::date),
    ('00000000-0000-0000-0000-000000000709', '00000000-0000-0000-0000-000000000605', 3, '19:30', '20:15', date_trunc('year', current_date)::date),
    ('00000000-0000-0000-0000-000000000710', '00000000-0000-0000-0000-000000000606', 6, '09:00', '13:00', date_trunc('year', current_date)::date);

insert into lezione_spazio (lezione_id, spazio_id) values
    -- acquaticità: vasca piccola
    ('00000000-0000-0000-0000-000000000701', '00000000-0000-0000-0000-0000000000c2'),
    ('00000000-0000-0000-0000-000000000702', '00000000-0000-0000-0000-0000000000c2'),
    -- delfini: corsie 1-2; pinguini: corsie 3-4 (il venerdì vasca piccola)
    ('00000000-0000-0000-0000-000000000703', '00000000-0000-0000-0000-000000000101'),
    ('00000000-0000-0000-0000-000000000703', '00000000-0000-0000-0000-000000000102'),
    ('00000000-0000-0000-0000-000000000704', '00000000-0000-0000-0000-000000000101'),
    ('00000000-0000-0000-0000-000000000704', '00000000-0000-0000-0000-000000000102'),
    ('00000000-0000-0000-0000-000000000705', '00000000-0000-0000-0000-000000000103'),
    ('00000000-0000-0000-0000-000000000705', '00000000-0000-0000-0000-000000000104'),
    ('00000000-0000-0000-0000-000000000706', '00000000-0000-0000-0000-0000000000c2'),
    -- agonismo: corsie 4-6
    ('00000000-0000-0000-0000-000000000707', '00000000-0000-0000-0000-000000000104'),
    ('00000000-0000-0000-0000-000000000707', '00000000-0000-0000-0000-000000000105'),
    ('00000000-0000-0000-0000-000000000707', '00000000-0000-0000-0000-000000000106'),
    ('00000000-0000-0000-0000-000000000708', '00000000-0000-0000-0000-000000000104'),
    ('00000000-0000-0000-0000-000000000708', '00000000-0000-0000-0000-000000000105'),
    ('00000000-0000-0000-0000-000000000708', '00000000-0000-0000-0000-000000000106'),
    -- acquagym: vasca piccola; nuoto libero: tutta la vasca grande
    ('00000000-0000-0000-0000-000000000709', '00000000-0000-0000-0000-0000000000c2'),
    ('00000000-0000-0000-0000-000000000710', '00000000-0000-0000-0000-0000000000c1');

-- Eventi nelle prossime settimane
-- La lezione di prova di mercoledì prende la corsia 2, già dei delfini: il piano la segnala come sovrapposizione
insert into evento (id, impianto_id, tipo, titolo, data, inizio, fine, istruttore_id, note)
select '00000000-0000-0000-0000-000000000802', '00000000-0000-0000-0000-0000000000b1', 'LEZIONE_PROVA', 'Lezione di prova scuola nuoto',
       d::date, '17:00', '17:45', '00000000-0000-0000-0000-000000000104', null
from generate_series(current_date + 1, current_date + 7, interval '1 day') g(d) where extract(isodow from d) = 3;
insert into evento_spazio (evento_id, spazio_id) values ('00000000-0000-0000-0000-000000000802', '00000000-0000-0000-0000-000000000102');

insert into evento (id, impianto_id, tipo, titolo, data, inizio, fine, note)
select '00000000-0000-0000-0000-000000000801', '00000000-0000-0000-0000-0000000000b1', 'GARA', 'Trofeo d''autunno esordienti', d::date, '14:00', '19:00', 'Vasca grande riservata'
from generate_series(current_date + 8, current_date + 14, interval '1 day') g(d) where extract(isodow from d) = 6;
insert into evento_spazio (evento_id, spazio_id) values ('00000000-0000-0000-0000-000000000801', '00000000-0000-0000-0000-0000000000c1');

insert into evento (impianto_id, tipo, titolo, data, data_fine, inizio, fine, note)
values ('00000000-0000-0000-0000-0000000000b1', 'CHIUSURA', 'Chiusura per le feste',
        make_date(extract(year from current_date)::int, 12, 24), make_date(extract(year from current_date)::int + 1, 1, 6),
        '00:00', '23:59', 'Impianto chiuso');
