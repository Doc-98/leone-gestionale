-- Staff degli spogliatoi demo, organizzato come il foglio turni vero della società ma con persone inventate:
-- spogliatoio maschile a 3 postazioni, femminile a 2, piccoli a 4 nei pomeriggi feriali e fino a 6 il sabato mattina,
-- l'atrio ai tornelli, la domenica mattina i piccoli. Chi apre arriva un po' prima degli altri (es. 15:30 invece delle 16).
-- Turni dal mese scorso al prossimo, generati dal modello settimanale; qualche turno scoperto e due richieste aperte.
-- Password di tutti: demo1234

-- ── Si riparte da zero con turni, richieste e modello ───────────────────────
delete from richiesta_turno;
delete from turno;
delete from modello_riga;

-- ── Postazioni ──────────────────────────────────────────────────────────────
insert into postazione (id, mansione_id, spazio_id, nome, ordine) values
    ('00000000-0000-0000-0000-000000000406', '00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-0000000000c3', 'Spogliatoio M · postazione 3', 3),
    ('00000000-0000-0000-0000-000000000407', '00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-0000000000c4', 'Spogliatoio F · postazione 2', 5),
    ('00000000-0000-0000-0000-000000000408', '00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-0000000000c5', 'Spogliatoio piccoli · postazione 2', 7),
    ('00000000-0000-0000-0000-000000000409', '00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-0000000000c5', 'Spogliatoio piccoli · postazione 3', 8),
    ('00000000-0000-0000-0000-000000000410', '00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-0000000000c5', 'Spogliatoio piccoli · postazione 4', 9),
    ('00000000-0000-0000-0000-000000000411', '00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-0000000000c5', 'Spogliatoio piccoli · postazione 5', 10),
    ('00000000-0000-0000-0000-000000000412', '00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-0000000000c5', 'Spogliatoio piccoli · postazione 6', 11);
update postazione set ordine = 4 where id = '00000000-0000-0000-0000-000000000403';
update postazione set ordine = 6 where id = '00000000-0000-0000-0000-000000000404';
update postazione set ordine = 12 where id = '00000000-0000-0000-0000-000000000405';

-- ── Persone (inventate) ─────────────────────────────────────────────────────
create temporary table staff_demo (n int, nome text, cognome text);
insert into staff_demo values
    (1, 'Ruggero', 'Sala'), (2, 'Carlo', 'Brivio'), (3, 'Andrea', 'Pirovano'), (4, 'Daniele', 'Fossati'), (5, 'Simone', 'Galimberti'),
    (6, 'Giacomo', 'Redaelli'), (7, 'Rayan', 'Haddad'), (8, 'Wei', 'Chen'), (9, 'Diego', 'Santambrogio'), (10, 'Alberto', 'Crippa'),
    (11, 'Marina', 'Colombo'), (12, 'Serena', 'Mariani'), (13, 'Eleonora', 'Ghezzi'), (14, 'Francesca', 'Pinna'), (15, 'Francesca', 'Nava'),
    (16, 'Carolina', 'Motta'), (17, 'Giada', 'Longoni'), (18, 'Giulia', 'Lissoni'), (19, 'Alice', 'Meroni'), (20, 'Cristina', 'Barzaghi'),
    (21, 'Greta', 'Sormani'), (22, 'Gaia', 'Bossi'), (23, 'Sofia', 'Radaelli'), (24, 'Camilla', 'Mapelli'), (25, 'Arianna', 'Lazzari'),
    (26, 'Antonio', 'Agrati');

insert into persona (id, nome, cognome, email)
select ('00000000-0000-0000-0000-00000000' || (1300 + n)::text)::uuid, nome, cognome,
       lower(nome || '.' || replace(cognome, ' ', '')) || '@demo.leone'
from staff_demo;
insert into account (id, persona_id, email, password_hash)
select ('00000000-0000-0000-0000-00000000' || (1400 + n)::text)::uuid, ('00000000-0000-0000-0000-00000000' || (1300 + n)::text)::uuid,
       lower(nome || '.' || replace(cognome, ' ', '')) || '@demo.leone', '{bcrypt}$2a$10$QDF4Rqled7UdqqJMA/RyVuJ77K5utaMoZgNvPgOuQvUCzoUGoIw4e'
from staff_demo;
insert into assegnazione_ruolo (account_id, ruolo, impianto_id, dal)
select ('00000000-0000-0000-0000-00000000' || (1400 + n)::text)::uuid, 'STAFF', '00000000-0000-0000-0000-0000000000b1', current_date - 400
from staff_demo;

-- ── Modello settimanale ─────────────────────────────────────────────────────
-- giorno (1 = lunedì), postazione, orario, persona: un numero di staff_demo, oppure 102 Paolo, 103 Luca, 104 Giulia,
-- 105 Marta (gli account demo), oppure null = turno scoperto
create temporary table giro_demo (giorno int, postazione text, inizio time, fine time, chi int);
insert into giro_demo values
    -- lunedì
    (1, 'M1', '16:00', '20:00', 1), (1, 'M2', '16:00', '19:00', 3), (1, 'M3', '16:00', '19:00', 103),
    (1, 'F1', '15:30', '20:00', 11), (1, 'F2', '15:30', '20:00', 12),
    (1, 'P1', '15:30', '19:15', 17), (1, 'P2', '16:00', '19:15', 15), (1, 'P3', '16:00', '19:15', 24), (1, 'P4', '16:00', '19:15', 22),
    (1, 'A', '15:30', '19:15', 10),
    -- martedì
    (2, 'M1', '16:00', '19:00', 1), (2, 'M2', '16:00', '20:00', 2), (2, 'M3', '16:00', '19:00', 4),
    (2, 'F1', '14:15', '20:00', 13), (2, 'F2', '16:00', '19:00', 14),
    (2, 'P1', '15:30', '19:15', 105), (2, 'P2', '16:00', '19:15', 15), (2, 'P3', '16:00', '19:15', 104), (2, 'P4', '16:00', '19:15', 6),
    (2, 'A', '15:30', '19:15', 26),
    -- mercoledì: si comincia prima, con la Propaganda
    (3, 'M1', '14:15', '18:30', 1), (3, 'M2', '15:00', '19:45', 6), (3, 'M3', '15:00', '19:00', 103),
    (3, 'F1', '14:15', '20:00', 13), (3, 'F2', '15:00', '19:00', 14),
    (3, 'P1', '14:15', '18:00', 105), (3, 'P2', '15:00', '19:00', 19), (3, 'P3', '15:00', '19:00', 8), (3, 'P4', '14:15', '18:00', 22),
    (3, 'A', '15:00', '19:15', 25),
    -- giovedì
    (4, 'M1', '16:00', '20:00', 1), (4, 'M2', '16:00', '19:00', 2), (4, 'M3', '16:00', '19:00', 9),
    (4, 'F1', '15:30', '20:00', 11), (4, 'F2', '16:00', '19:00', 16),
    (4, 'P1', '15:30', '19:15', 13), (4, 'P2', '16:00', '19:15', 104), (4, 'P3', '16:00', '19:15', 7), (4, 'P4', '16:00', '19:15', 22),
    (4, 'A', '15:30', '19:15', 10),
    -- venerdì
    (5, 'M1', '13:30', '18:30', 1), (5, 'M2', '15:00', '20:00', 5), (5, 'M3', '14:00', '19:00', 7),
    (5, 'F1', '13:30', '20:00', 12), (5, 'F2', '15:00', '19:00', 105),
    (5, 'P1', '15:00', '19:15', 19), (5, 'P2', '15:00', '19:15', 17), (5, 'P3', '15:00', '19:15', 8), (5, 'P4', '15:00', '19:15', 4),
    (5, 'P5', '15:00', '19:15', null),
    (5, 'A', '15:00', '19:15', 26),
    -- sabato
    (6, 'M1', '09:30', '13:15', 103), (6, 'M2', '09:30', '15:15', 5), (6, 'M3', '15:10', '17:30', 1),
    (6, 'F1', '09:30', '13:30', 18), (6, 'F2', '13:30', '18:30', 11),
    (6, 'P1', '08:45', '13:30', 21), (6, 'P2', '08:45', '13:30', 22), (6, 'P3', '09:30', '13:30', 20), (6, 'P4', '09:30', '13:30', 17),
    (6, 'P5', '09:30', '13:30', 23), (6, 'P6', '09:30', '13:30', 16),
    (6, 'A', '09:30', '13:30', 102),
    -- domenica: Acquababy e ambientamento
    (7, 'P1', '09:00', '13:00', 17), (7, 'P2', '09:00', '13:00', null);

create temporary table postazioni_demo (codice text primary key, id uuid);
insert into postazioni_demo values
    ('M1', '00000000-0000-0000-0000-000000000401'), ('M2', '00000000-0000-0000-0000-000000000402'), ('M3', '00000000-0000-0000-0000-000000000406'),
    ('F1', '00000000-0000-0000-0000-000000000403'), ('F2', '00000000-0000-0000-0000-000000000407'),
    ('P1', '00000000-0000-0000-0000-000000000404'), ('P2', '00000000-0000-0000-0000-000000000408'), ('P3', '00000000-0000-0000-0000-000000000409'),
    ('P4', '00000000-0000-0000-0000-000000000410'), ('P5', '00000000-0000-0000-0000-000000000411'), ('P6', '00000000-0000-0000-0000-000000000412'),
    ('A', '00000000-0000-0000-0000-000000000405');

create temporary table righe_demo as
select gen_random_uuid() as id, g.giorno, p.id as postazione, g.inizio, g.fine,
       case when g.chi is null then null
            when g.chi > 100 then ('00000000-0000-0000-0000-000000000' || g.chi::text)::uuid
            else ('00000000-0000-0000-0000-00000000' || (1300 + g.chi)::text)::uuid end as persona
from giro_demo g join postazioni_demo p on p.codice = g.postazione;

insert into modello_riga (id, modello_id, postazione_id, giorno_settimana, inizio, fine, persona_id)
select id, '00000000-0000-0000-0000-000000000501', postazione, giorno, inizio, fine, persona from righe_demo;

-- ── Turni dal mese scorso a quello prossimo ─────────────────────────────────
-- I turni scoperti solo da domani in poi (quelli passati, in un foglio vero, sarebbero stati coperti)
insert into turno (persona_id, postazione_id, data, inizio, fine, modello_riga_id)
select r.persona, r.postazione, g.giorno::date, r.inizio, r.fine, r.id
from generate_series(date_trunc('month', current_date) - interval '1 month',
                     date_trunc('month', current_date) + interval '2 months' - interval '1 day',
                     interval '1 day') as g(giorno)
join righe_demo r on extract(isodow from g.giorno) = r.giorno
where r.persona is not null or g.giorno::date > current_date;

-- ── Richieste aperte ────────────────────────────────────────────────────────
-- Giulia non può fare il prossimo martedì e cerca qualcuno che la sostituisca
insert into richiesta_turno (tipo, turno_id, richiedente_id, messaggio, stato)
select 'SOSTITUZIONE', t.id, t.persona_id, 'Ho un esame, qualcuno può coprirmi?', 'IN_ATTESA_COLLEGA'
from turno t
where t.persona_id = '00000000-0000-0000-0000-000000000104' and t.data > current_date and extract(isodow from t.data) = 2
order by t.data limit 1;

-- Marta chiede di cominciare più tardi il prossimo venerdì: tocca al coordinatore approvare
insert into richiesta_turno (tipo, turno_id, richiedente_id, nuovo_inizio, nuovo_fine, messaggio, stato)
select 'CAMBIO_ORARIO', t.id, t.persona_id, '16:00', '19:00', 'Esco tardi da lezione', 'IN_ATTESA_APPROVAZIONE'
from turno t
where t.persona_id = '00000000-0000-0000-0000-000000000105' and t.data > current_date and extract(isodow from t.data) = 5
order by t.data limit 1;

drop table righe_demo;
drop table postazioni_demo;
drop table giro_demo;
drop table staff_demo;
