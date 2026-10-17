-- Dati demo, solo per sviluppo e test (profilo «demo»). Persone inventate.
-- Password di tutti gli account demo: demo1234
-- I turni sono generati intorno alla data di oggi, così la demo mostra sempre turni recenti e in arrivo.

insert into disciplina (id, codice, nome) values
    ('00000000-0000-0000-0000-0000000000d1', 'NUOTO', 'Nuoto');

insert into sede (id, nome, indirizzo) values
    ('00000000-0000-0000-0000-0000000000a1', 'Leone XIII Sport', 'Milano');

insert into impianto (id, sede_id, disciplina_id, nome) values
    ('00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000d1', 'Piscina');

insert into spazio (id, impianto_id, nome, tipo, ordine) values
    ('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-0000000000b1', 'Vasca grande', 'VASCA', 1),
    ('00000000-0000-0000-0000-0000000000c2', '00000000-0000-0000-0000-0000000000b1', 'Vasca piccola', 'VASCA', 2),
    ('00000000-0000-0000-0000-0000000000c3', '00000000-0000-0000-0000-0000000000b1', 'Spogliatoio maschile', 'SPOGLIATOIO', 3),
    ('00000000-0000-0000-0000-0000000000c4', '00000000-0000-0000-0000-0000000000b1', 'Spogliatoio femminile', 'SPOGLIATOIO', 4),
    ('00000000-0000-0000-0000-0000000000c5', '00000000-0000-0000-0000-0000000000b1', 'Spogliatoio piccoli', 'SPOGLIATOIO', 5),
    ('00000000-0000-0000-0000-0000000000c6', '00000000-0000-0000-0000-0000000000b1', 'Atrio', 'ATRIO', 6);

insert into persona (id, nome, cognome, email) values
    ('00000000-0000-0000-0000-000000000101', 'Chiara', 'Ferri', 'admin@demo.leone'),
    ('00000000-0000-0000-0000-000000000102', 'Paolo', 'Galli', 'coordinatore@demo.leone'),
    ('00000000-0000-0000-0000-000000000103', 'Luca', 'Bianchi', 'staff@demo.leone'),
    ('00000000-0000-0000-0000-000000000104', 'Giulia', 'Conti', 'giulia@demo.leone'),
    ('00000000-0000-0000-0000-000000000105', 'Marta', 'Ricci', 'marta@demo.leone');

insert into account (id, persona_id, email, password_hash) values
    ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000101', 'admin@demo.leone', '{bcrypt}$2a$10$QDF4Rqled7UdqqJMA/RyVuJ77K5utaMoZgNvPgOuQvUCzoUGoIw4e'),
    ('00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-000000000102', 'coordinatore@demo.leone', '{bcrypt}$2a$10$QDF4Rqled7UdqqJMA/RyVuJ77K5utaMoZgNvPgOuQvUCzoUGoIw4e'),
    ('00000000-0000-0000-0000-000000000203', '00000000-0000-0000-0000-000000000103', 'staff@demo.leone', '{bcrypt}$2a$10$QDF4Rqled7UdqqJMA/RyVuJ77K5utaMoZgNvPgOuQvUCzoUGoIw4e'),
    ('00000000-0000-0000-0000-000000000204', '00000000-0000-0000-0000-000000000104', 'giulia@demo.leone', '{bcrypt}$2a$10$QDF4Rqled7UdqqJMA/RyVuJ77K5utaMoZgNvPgOuQvUCzoUGoIw4e'),
    ('00000000-0000-0000-0000-000000000205', '00000000-0000-0000-0000-000000000105', 'marta@demo.leone', '{bcrypt}$2a$10$QDF4Rqled7UdqqJMA/RyVuJ77K5utaMoZgNvPgOuQvUCzoUGoIw4e');

insert into assegnazione_ruolo (account_id, ruolo, impianto_id) values
    ('00000000-0000-0000-0000-000000000201', 'ADMIN', null),
    ('00000000-0000-0000-0000-000000000202', 'COORDINATORE', '00000000-0000-0000-0000-0000000000b1'),
    ('00000000-0000-0000-0000-000000000202', 'STAFF', '00000000-0000-0000-0000-0000000000b1'),
    ('00000000-0000-0000-0000-000000000203', 'STAFF', '00000000-0000-0000-0000-0000000000b1'),
    ('00000000-0000-0000-0000-000000000204', 'STAFF', '00000000-0000-0000-0000-0000000000b1'),
    ('00000000-0000-0000-0000-000000000205', 'STAFF', '00000000-0000-0000-0000-0000000000b1');

insert into mansione (id, impianto_id, codice, nome) values
    ('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-0000000000b1', 'ASS_SPOGLIATOIO', 'Assistente spogliatoio'),
    ('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-0000000000b1', 'ASS_ATRIO', 'Assistente atrio');

insert into postazione (id, mansione_id, spazio_id, nome, ordine) values
    ('00000000-0000-0000-0000-000000000401', '00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-0000000000c3', 'Spogliatoio M · postazione 1', 1),
    ('00000000-0000-0000-0000-000000000402', '00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-0000000000c3', 'Spogliatoio M · postazione 2', 2),
    ('00000000-0000-0000-0000-000000000403', '00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-0000000000c4', 'Spogliatoio F · postazione 1', 3),
    ('00000000-0000-0000-0000-000000000404', '00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-0000000000c5', 'Spogliatoio piccoli · postazione 1', 4),
    ('00000000-0000-0000-0000-000000000405', '00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-0000000000c6', 'Atrio · tornelli', 5);

-- Turni dal mese scorso a quello prossimo: un giro settimanale per ognuno dello staff.
-- dow: 1 = lunedì … 6 = sabato.
insert into turno (persona_id, postazione_id, data, inizio, fine)
select r.persona_id, r.postazione_id, g.giorno::date, r.inizio, r.fine
from generate_series(date_trunc('month', current_date) - interval '1 month',
                     date_trunc('month', current_date) + interval '2 months' - interval '1 day',
                     interval '1 day') as g(giorno)
join (values
    ('00000000-0000-0000-0000-000000000103'::uuid, '00000000-0000-0000-0000-000000000402'::uuid, 1, time '15:00', time '20:00'),
    ('00000000-0000-0000-0000-000000000103'::uuid, '00000000-0000-0000-0000-000000000405'::uuid, 3, time '15:45', time '19:15'),
    ('00000000-0000-0000-0000-000000000103'::uuid, '00000000-0000-0000-0000-000000000401'::uuid, 6, time '09:30', time '13:15'),
    ('00000000-0000-0000-0000-000000000104'::uuid, '00000000-0000-0000-0000-000000000404'::uuid, 2, time '15:30', time '19:15'),
    ('00000000-0000-0000-0000-000000000104'::uuid, '00000000-0000-0000-0000-000000000404'::uuid, 4, time '15:30', time '19:15'),
    ('00000000-0000-0000-0000-000000000105'::uuid, '00000000-0000-0000-0000-000000000403'::uuid, 1, time '15:00', time '20:00'),
    ('00000000-0000-0000-0000-000000000105'::uuid, '00000000-0000-0000-0000-000000000405'::uuid, 5, time '15:45', time '19:15'),
    ('00000000-0000-0000-0000-000000000102'::uuid, '00000000-0000-0000-0000-000000000405'::uuid, 6, time '09:00', time '13:00')
) as r(persona_id, postazione_id, dow, inizio, fine)
  on extract(isodow from g.giorno) = r.dow;
