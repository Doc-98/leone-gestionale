-- Piano vasca demo con tutti i turni di tutti i corsi della pagina «Piscina» del sito della società (stagione
-- 2026-27): Acquababy, ambientamento, scuola nuoto, squali, Master Junior, Propaganda, corso ragazzi, corsi leoniani,
-- nuoto adulti, AquaFitness e nuoto libero. Sul sito mancano gli orari di Acquagol e del corso gestanti: non ci sono.
-- Le corsie le ho assegnate io (un corso per corsia, la vasca grande a 6 corsie e il mercoledì a 5).
-- Istruttori: i responsabili indicati sul sito (Propaganda e corsi leoniani) più istruttori inventati.
-- Iscritti: tutti inventati. Chi era già iscritto resta nello stesso corso; i posti liberi si riempiono con persone
-- nuove dell'età giusta, così nessun turno resta vuoto.

-- ── Le iscrizioni e le attese di prima, da rimettere sui turni nuovi ───────
create temporary table stagione_corrente as
select id, nome, dal, al, extract(year from dal)::int as anno from stagione where current_date between dal and al;

create temporary table vecchie_iscrizioni as
select i.persona_id as persona, l.corso_id as corso, l.giorno_settimana as giorno, l.inizio, i.dal, i.al,
       p.id::text like '00000000-0000-0000-0000-%' as fissa,
       (select anno from stagione_corrente) - extract(year from p.data_nascita)::int as eta
from iscrizione i join lezione l on l.id = i.lezione_id join persona p on p.id = i.persona_id;

create temporary table vecchie_attese as
select a.persona_id as persona, l.corso_id as corso, l.giorno_settimana as giorno, l.inizio, a.aggiunta_il, a.aggiunta_da,
       a.note, a.avvisata_il, a.stato, a.chiusa_il
from attesa a join lezione l on l.id = a.lezione_id;

delete from presenza;
delete from attesa;
delete from iscrizione;
delete from fascia_oraria;
delete from lezione;

-- ── Istruttori ──────────────────────────────────────────────────────────────
-- 1110 e 1111 sono i responsabili indicati sul sito; gli altri sono inventati. Password: demo1234
insert into persona (id, nome, cognome, email) values
    ('00000000-0000-0000-0000-000000001101', 'Silvia', 'Brambilla', 'silvia.brambilla@demo.leone'),
    ('00000000-0000-0000-0000-000000001102', 'Federico', 'Mauri', 'federico.mauri@demo.leone'),
    ('00000000-0000-0000-0000-000000001103', 'Valentina', 'Corti', 'valentina.corti@demo.leone'),
    ('00000000-0000-0000-0000-000000001104', 'Stefano', 'Bonetti', 'stefano.bonetti@demo.leone'),
    ('00000000-0000-0000-0000-000000001105', 'Laura', 'Pozzi', 'laura.pozzi@demo.leone'),
    ('00000000-0000-0000-0000-000000001106', 'Michele', 'Arosio', 'michele.arosio@demo.leone'),
    ('00000000-0000-0000-0000-000000001107', 'Francesca', 'Zanetti', 'francesca.zanetti@demo.leone'),
    ('00000000-0000-0000-0000-000000001108', 'Roberto', 'Galbusera', 'roberto.galbusera@demo.leone'),
    ('00000000-0000-0000-0000-000000001109', 'Ilaria', 'Vergani', 'ilaria.vergani@demo.leone'),
    ('00000000-0000-0000-0000-000000001110', 'Gianpaolo', 'Bussolin', 'bussolin@demo.leone'),
    ('00000000-0000-0000-0000-000000001111', 'Patrizia', 'De Donato', 'dedonato@demo.leone'),
    ('00000000-0000-0000-0000-000000001112', 'Marco', 'Sironi', 'marco.sironi@demo.leone');
insert into account (id, persona_id, email, password_hash)
select ('00000000-0000-0000-0000-00000000' || (1200 + n)::text)::uuid, ('00000000-0000-0000-0000-00000000' || (1100 + n)::text)::uuid,
       (select email from persona where id = ('00000000-0000-0000-0000-00000000' || (1100 + n)::text)::uuid),
       '{bcrypt}$2a$10$QDF4Rqled7UdqqJMA/RyVuJ77K5utaMoZgNvPgOuQvUCzoUGoIw4e'
from generate_series(1, 12) n;
insert into assegnazione_ruolo (account_id, ruolo, impianto_id, dal)
select ('00000000-0000-0000-0000-00000000' || (1200 + n)::text)::uuid, 'STAFF', '00000000-0000-0000-0000-0000000000b1', current_date - 400
from generate_series(1, 12) n;
-- il responsabile tecnico coordina anche il piano vasca
insert into assegnazione_ruolo (account_id, ruolo, impianto_id, dal)
values ('00000000-0000-0000-0000-000000001210', 'COORDINATORE', '00000000-0000-0000-0000-0000000000b1', current_date - 400);

-- ── Corsi ───────────────────────────────────────────────────────────────────
-- I gruppi per anno di nascita del sito diventano corsi distinti (età nella stagione: anno della stagione − anno di nascita)
update corso set nome = 'Acquababy 0-1 anni', livello = 'Con un genitore', eta_da = 0, eta_a = 1,
       istruttore_id = '00000000-0000-0000-0000-000000000108', colore = '#F2B5C4' where id = '00000000-0000-0000-0000-000000000611';
update corso set nome = 'Ambientamento vasca piccola · 4 anni', eta_da = 4, eta_a = 4,
       istruttore_id = '00000000-0000-0000-0000-000000001109', colore = '#9AD1D4' where id = '00000000-0000-0000-0000-000000000612';
update corso set nome = 'Ambientamento vasca alta', eta_da = 4, eta_a = 5,
       istruttore_id = '00000000-0000-0000-0000-000000001101', colore = '#6FB7BF' where id = '00000000-0000-0000-0000-000000000613';
update corso set eta_da = 5, eta_a = 5, istruttore_id = '00000000-0000-0000-0000-000000001105' where id = '00000000-0000-0000-0000-000000000614';
update corso set eta_da = 6, eta_a = 6, istruttore_id = '00000000-0000-0000-0000-000000001102' where id = '00000000-0000-0000-0000-000000000615';
update corso set eta_da = 6, eta_a = 7, istruttore_id = '00000000-0000-0000-0000-000000000107' where id = '00000000-0000-0000-0000-000000000616';
update corso set eta_da = 7, eta_a = 8, istruttore_id = '00000000-0000-0000-0000-000000000106' where id = '00000000-0000-0000-0000-000000000617';
update corso set eta_da = 8, eta_a = 9, istruttore_id = '00000000-0000-0000-0000-000000001103' where id = '00000000-0000-0000-0000-000000000618';
update corso set eta_da = 9, eta_a = 11, istruttore_id = '00000000-0000-0000-0000-000000000109' where id = '00000000-0000-0000-0000-000000000619';
update corso set eta_da = 10, eta_a = 12, istruttore_id = '00000000-0000-0000-0000-000000001104' where id = '00000000-0000-0000-0000-000000000620';
update corso set eta_da = 11, eta_a = 13, istruttore_id = '00000000-0000-0000-0000-000000001106' where id = '00000000-0000-0000-0000-000000000621';
update corso set nome = 'Propaganda · 8-12 anni', eta_da = 8, eta_a = 12,
       istruttore_id = '00000000-0000-0000-0000-000000001110', colore = '#1F4E79' where id = '00000000-0000-0000-0000-000000000622';
update corso set eta_da = 13, eta_a = 17, istruttore_id = '00000000-0000-0000-0000-000000001106', colore = '#7A5C99' where id = '00000000-0000-0000-0000-000000000623';
update corso set eta_da = 18, eta_a = null, istruttore_id = '00000000-0000-0000-0000-000000000108', colore = '#E9A23B' where id = '00000000-0000-0000-0000-000000000624';
update corso set colore = '#A7B4C2' where id = '00000000-0000-0000-0000-000000000625';

insert into corso (id, impianto_id, nome, tipo, livello, eta_da, eta_a, istruttore_id, colore) values
    ('00000000-0000-0000-0000-000000000626', '00000000-0000-0000-0000-0000000000b1', 'Acquababy 1-2 anni', 'ACQUATICITA', 'Con un genitore', 1, 2, '00000000-0000-0000-0000-000000000108', '#E89AAE'),
    ('00000000-0000-0000-0000-000000000627', '00000000-0000-0000-0000-0000000000b1', 'Acquababy 2-3 anni', 'ACQUATICITA', 'Con un genitore', 2, 3, '00000000-0000-0000-0000-000000000108', '#D9788F'),
    ('00000000-0000-0000-0000-000000000628', '00000000-0000-0000-0000-0000000000b1', 'Ambientamento vasca piccola · 3 anni', 'ACQUATICITA', null, 3, 3, '00000000-0000-0000-0000-000000001109', '#BFE3E5'),
    ('00000000-0000-0000-0000-000000000629', '00000000-0000-0000-0000-0000000000b1', 'Propaganda · dai 13 anni', 'AGONISMO', 'Propaganda FIN', 13, 22, '00000000-0000-0000-0000-000000001110', '#16385A'),
    ('00000000-0000-0000-0000-000000000630', '00000000-0000-0000-0000-0000000000b1', 'Corso ragazzi · base e avanzato', 'SCUOLA_NUOTO', 'Medie e liceo', 11, 18, '00000000-0000-0000-0000-000000001104', '#4F6D7A'),
    ('00000000-0000-0000-0000-000000000631', '00000000-0000-0000-0000-0000000000b1', 'Leoniani · infanzia', 'SCUOLA_NUOTO', 'Corsi leoniani', 3, 5, '00000000-0000-0000-0000-000000001111', '#C9A227'),
    ('00000000-0000-0000-0000-000000000632', '00000000-0000-0000-0000-0000000000b1', 'Leoniani · primaria', 'SCUOLA_NUOTO', 'Corsi leoniani', 6, 10, '00000000-0000-0000-0000-000000001111', '#B08A1E'),
    ('00000000-0000-0000-0000-000000000633', '00000000-0000-0000-0000-0000000000b1', 'Leoniani · secondaria', 'SCUOLA_NUOTO', 'Corsi leoniani', 11, 13, '00000000-0000-0000-0000-000000001111', '#8C6D14'),
    ('00000000-0000-0000-0000-000000000634', '00000000-0000-0000-0000-0000000000b1', 'Adulti · Easy Master', 'MASTER', 'Adulti', 18, null, '00000000-0000-0000-0000-000000001107', '#7FB069'),
    ('00000000-0000-0000-0000-000000000635', '00000000-0000-0000-0000-0000000000b1', 'Adulti · intermedio', 'MASTER', 'Adulti', 18, null, '00000000-0000-0000-0000-000000001108', '#5C9A4B'),
    ('00000000-0000-0000-0000-000000000636', '00000000-0000-0000-0000-0000000000b1', 'Adulti · avanzato', 'MASTER', 'Adulti', 18, null, '00000000-0000-0000-0000-000000001112', '#3E7C35'),
    ('00000000-0000-0000-0000-000000000637', '00000000-0000-0000-0000-0000000000b1', 'Adulti · base', 'MASTER', 'Adulti', 18, null, '00000000-0000-0000-0000-000000001112', '#A5CC93');

-- Età (nella stagione) di chi si iscrive a ogni corso, e posti per turno
create temporary table corso_demo (corso uuid primary key, codice text unique, eta_da int, eta_a int, posti int);
insert into corso_demo values
    ('00000000-0000-0000-0000-000000000611', 'AB0', 0, 0, 6),
    ('00000000-0000-0000-0000-000000000626', 'AB1', 1, 1, 6),
    ('00000000-0000-0000-0000-000000000627', 'AB2', 2, 2, 6),
    ('00000000-0000-0000-0000-000000000628', 'AP3', 3, 3, 6),
    ('00000000-0000-0000-0000-000000000612', 'AP4', 4, 4, 6),
    ('00000000-0000-0000-0000-000000000613', 'AA', 4, 5, 6),
    ('00000000-0000-0000-0000-000000000614', 'ST', 5, 5, 7),
    ('00000000-0000-0000-0000-000000000615', 'DG', 6, 6, 8),
    ('00000000-0000-0000-0000-000000000616', 'DV', 6, 7, 8),
    ('00000000-0000-0000-0000-000000000617', 'DA', 7, 8, 8),
    ('00000000-0000-0000-0000-000000000618', 'DR', 8, 9, 8),
    ('00000000-0000-0000-0000-000000000619', 'DN', 9, 11, 8),
    ('00000000-0000-0000-0000-000000000620', 'SB', 10, 12, 8),
    ('00000000-0000-0000-0000-000000000621', 'SA', 11, 13, 8),
    ('00000000-0000-0000-0000-000000000623', 'MJ', 13, 17, 10),
    ('00000000-0000-0000-0000-000000000622', 'PR', 8, 12, 14),
    ('00000000-0000-0000-0000-000000000629', 'PG', 13, 18, 10),
    ('00000000-0000-0000-0000-000000000630', 'CR', 11, 18, 12),
    ('00000000-0000-0000-0000-000000000631', 'LI', 3, 5, 8),
    ('00000000-0000-0000-0000-000000000632', 'LP', 6, 10, 14),
    ('00000000-0000-0000-0000-000000000633', 'LS', 11, 13, 8),
    ('00000000-0000-0000-0000-000000000634', 'EM', 25, 70, 8),
    ('00000000-0000-0000-0000-000000000635', 'IN', 22, 65, 8),
    ('00000000-0000-0000-0000-000000000636', 'AV', 20, 60, 8),
    ('00000000-0000-0000-0000-000000000637', 'BA', 30, 75, 8),
    ('00000000-0000-0000-0000-000000000624', 'AF', 20, 60, 16),
    ('00000000-0000-0000-0000-000000000625', 'NL', null, null, null);

-- ── Lezioni ─────────────────────────────────────────────────────────────────
-- Spazi: C1…C6 le corsie della vasca grande a 6, W1…W5 quelle a 5 (il mercoledì), P la vasca piccola.
create temporary table piano_sito (codice text, giorni int[], inizio time, fine time, spazi text);
insert into piano_sito values
    -- nuoto adulti e nuoto libero, mattina
    ('EM', '{1,2,4}', '07:00', '07:45', 'C1'), ('AV', '{1,2,4}', '07:00', '07:45', 'C2'), ('NL', '{1,2,4}', '07:00', '08:00', 'C3 C4 C5 C6'),
    ('EM', '{3}', '07:00', '07:45', 'W1'), ('NL', '{3}', '07:00', '08:00', 'W2 W3 W4 W5'),
    ('EM', '{5}', '07:00', '07:45', 'C1'), ('NL', '{5}', '07:00', '08:00', 'C2 C3 C4 C5 C6'),
    ('EM', '{1,2,4}', '08:00', '08:45', 'C1'), ('IN', '{1,2,4}', '08:00', '08:45', 'C2'), ('AV', '{1,2,4}', '08:00', '08:45', 'C3'),
    ('AA', '{1,2}', '08:10', '08:55', 'C4'), ('NL', '{1,2}', '08:00', '09:00', 'C5 C6'), ('NL', '{4}', '08:00', '09:00', 'C4 C5 C6'),
    ('EM', '{1,2,4}', '08:50', '09:35', 'C1'), ('IN', '{1,2,4}', '08:50', '09:35', 'C2'), ('AV', '{1,2,4}', '08:50', '09:35', 'C3'),
    ('NL', '{1,2,4}', '09:00', '10:20', 'C4 C5 C6'),
    ('IN', '{1,4}', '09:35', '10:20', 'C1'), ('AV', '{2}', '09:35', '10:20', 'C1'), ('BA', '{2,4}', '09:35', '10:20', 'C2'),
    -- Acquababy (vasca piccola)
    ('AB2', '{1}', '08:50', '09:20', 'P'), ('AB1', '{1}', '09:20', '09:50', 'P'), ('AB0', '{1}', '09:50', '10:20', 'P'),
    ('AB1', '{2}', '08:50', '09:20', 'P'), ('AB2', '{2}', '09:20', '09:50', 'P'), ('AB0', '{2}', '09:50', '10:20', 'P'),
    ('AB2', '{4}', '08:20', '08:50', 'P'), ('AB0', '{4}', '08:50', '09:20', 'P'), ('AB1', '{4}', '09:20', '09:50', 'P'), ('AB0', '{4}', '09:50', '10:20', 'P'),
    ('AB0', '{6}', '08:00', '08:30', 'P'), ('AB2', '{6}', '08:30', '09:00', 'P'), ('AB0', '{6}', '09:00', '09:30', 'P'), ('AB2', '{6}', '09:30', '10:00', 'P'),
    ('AB1', '{6}', '10:00', '10:30', 'P'), ('AB2', '{6}', '17:00', '17:30', 'P'), ('AB1', '{6}', '17:30', '18:00', 'P'), ('AB2', '{6}', '18:00', '18:30', 'P'),
    ('AB0', '{6}', '18:30', '19:00', 'P'),
    ('AB2', '{7}', '09:00', '09:30', 'P'), ('AB0', '{7}', '09:30', '10:00', 'P'), ('AB0', '{7}', '10:00', '10:30', 'P'), ('AB2', '{7}', '10:30', '11:00', 'P'),
    ('AB1', '{7}', '11:00', '11:30', 'P'),
    -- pranzo: AquaFitness e nuoto libero
    ('NL', '{1,4}', '12:20', '13:25', 'C1 C2 C3 C4 C5 C6'), ('NL', '{2}', '12:20', '13:25', 'C3 C4 C5 C6'), ('AF', '{2}', '12:35', '13:20', 'C1 C2'),
    ('AF', '{1,2,4}', '13:25', '14:10', 'C1 C2'), ('NL', '{1,2,4}', '13:25', '14:25', 'C3 C4 C5 C6'),
    ('NL', '{3}', '13:25', '14:25', 'W1 W2 W3 W4 W5'),
    ('NL', '{5}', '12:00', '13:20', 'C3 C4 C5 C6'), ('AF', '{5}', '12:35', '13:20', 'C1 C2'),
    -- corsi leoniani
    ('LP', '{1,2,4}', '16:10', '16:55', 'C1 C2'), ('LP', '{3}', '16:10', '16:55', 'W1 W2'),
    ('LI', '{1,3,5}', '16:10', '16:55', 'P'), ('LI', '{4}', '16:10', '16:55', 'C3'),
    ('LS', '{5}', '13:40', '14:25', 'C1'),
    -- ambientamento vasca piccola
    ('AP4', '{1}', '16:55', '17:35', 'P'), ('AP3', '{1}', '17:40', '18:20', 'P'),
    ('AP4', '{2}', '16:10', '16:50', 'P'), ('AP3', '{2}', '16:55', '17:35', 'P'), ('AP3', '{2}', '17:40', '18:20', 'P'),
    ('AP3', '{3}', '15:25', '16:05', 'P'), ('AP3', '{3}', '16:55', '17:35', 'P'), ('AP4', '{3}', '17:40', '18:20', 'P'),
    ('AP3', '{4}', '16:10', '16:50', 'P'), ('AP4', '{4}', '16:55', '17:35', 'P'), ('AP3', '{4}', '17:40', '18:20', 'P'),
    ('AP4', '{5}', '15:25', '16:05', 'P'), ('AP3', '{5}', '16:55', '17:35', 'P'), ('AP3', '{5}', '17:40', '18:20', 'P'),
    ('AP3', '{6}', '10:30', '11:10', 'P'), ('AP3', '{6}', '11:10', '11:50', 'P'), ('AP4', '{6}', '11:50', '12:30', 'P'),
    ('AP3', '{7}', '11:30', '12:10', 'P'),
    -- lunedì, martedì, giovedì: la sesta corsia delle fasce di scuola nuoto, poi squali e Master Junior
    ('AA', '{1,2,4}', '17:00', '17:45', 'C6'), ('DN', '{1,2,4}', '17:50', '18:35', 'C6'),
    ('DN', '{1,4}', '18:40', '19:25', 'C1'), ('SB', '{1,4}', '18:40', '19:25', 'C2'), ('SA', '{1,4}', '18:40', '19:25', 'C3'), ('MJ', '{1,4}', '18:40', '19:25', 'C4'),
    ('SB', '{2}', '18:40', '19:25', 'C1'), ('SA', '{2}', '18:40', '19:25', 'C2'), ('MJ', '{2}', '18:40', '19:25', 'C3'),
    -- sera
    ('AF', '{1,5}', '19:30', '20:15', 'C1 C2'), ('NL', '{1,5}', '19:30', '20:20', 'C3 C4 C5 C6'),
    ('NL', '{2,4}', '19:30', '20:20', 'C1 C2 C3 C4 C5 C6'),
    ('EM', '{1,4}', '20:20', '21:15', 'C1'), ('IN', '{2}', '20:20', '21:15', 'C1'), ('NL', '{1,2,4}', '20:20', '21:15', 'C2 C3 C4 C5 C6'),
    ('NL', '{5}', '20:20', '21:15', 'C1 C2 C3 C4 C5 C6'),
    ('NL', '{3}', '19:30', '20:20', 'W1 W2 W3 W4 W5'), ('AF', '{3}', '20:20', '21:05', 'W1 W2'), ('NL', '{3}', '20:20', '21:15', 'W3 W4 W5'),
    -- mercoledì pomeriggio (vasca grande a 5 corsie)
    ('AA', '{3}', '14:30', '15:15', 'W1'), ('SB', '{3}', '14:30', '15:15', 'W2'), ('SA', '{3}', '14:30', '15:15', 'W3'), ('PG', '{3}', '14:30', '16:00', 'W5'),
    ('ST', '{3}', '15:20', '16:05', 'W1'), ('DG', '{3}', '15:20', '16:05', 'W2'), ('DV', '{3}', '15:20', '16:05', 'W3'), ('DA', '{3}', '15:20', '16:05', 'W4'),
    ('DN', '{3}', '17:00', '17:45', 'W1'), ('AA', '{3}', '17:00', '17:45', 'W2'), ('PR', '{3}', '17:00', '18:30', 'W4 W5'),
    ('DG', '{3}', '17:50', '18:35', 'W1'), ('DV', '{3}', '17:50', '18:35', 'W2'),
    ('SB', '{3}', '18:40', '19:25', 'W1'), ('SA', '{3}', '18:40', '19:25', 'W2'),
    -- venerdì
    ('CR', '{5}', '13:45', '15:15', 'C2 C3'),
    ('MJ', '{5}', '14:30', '15:15', 'C1'), ('SB', '{5}', '14:30', '15:15', 'C4'), ('SA', '{5}', '14:30', '15:15', 'C5'),
    ('AA', '{5}', '15:20', '16:05', 'C6'), ('DN', '{5}', '16:10', '16:55', 'C6'), ('AA', '{5}', '17:00', '17:45', 'C6'), ('DN', '{5}', '17:50', '18:35', 'C6'),
    ('SB', '{5}', '18:40', '19:25', 'C1'), ('SA', '{5}', '18:40', '19:25', 'C2'), ('MJ', '{5}', '18:40', '19:25', 'C3'),
    -- sabato
    ('IN', '{6}', '08:00', '08:45', 'C1'), ('AV', '{6}', '08:00', '08:45', 'C2'), ('NL', '{6}', '08:00', '09:00', 'C3 C4 C5 C6'),
    ('AF', '{6}', '09:00', '09:45', 'C1 C2'), ('AA', '{6}', '09:00', '09:45', 'C3'),
    ('AA', '{6}', '09:45', '10:30', 'C6'), ('DN', '{6}', '10:35', '11:20', 'C6'), ('AA', '{6}', '11:30', '12:15', 'C6'), ('DN', '{6}', '12:15', '13:00', 'C6'),
    ('SB', '{6}', '13:00', '13:45', 'C1'), ('SA', '{6}', '13:00', '13:45', 'C2'), ('MJ', '{6}', '14:00', '14:45', 'C1'),
    ('NL', '{6}', '18:05', '19:30', 'C1 C2 C3 C4 C5 C6'), ('NL', '{6}', '19:30', '21:00', 'C1 C2 C3 C4 C5 C6'),
    -- domenica
    ('NL', '{7}', '08:00', '12:30', 'C1 C2 C3 C4 C5 C6');

-- le fasce della scuola nuoto: Stella Marina e Delfini Giallo, Verde, Azzurro, Rosso nelle corsie 1-5
insert into piano_sito
select l.codice, f.giorni, f.inizio, f.fine, l.corsia
from (values ('ST', 'C1'), ('DG', 'C2'), ('DV', 'C3'), ('DA', 'C4'), ('DR', 'C5')) as l (codice, corsia),
     (values ('{1,2,4}'::int[], time '17:00', time '17:45'), ('{1,2,4}', '17:50', '18:35'),
             ('{5}', '15:20', '16:05'), ('{5}', '16:10', '16:55'), ('{5}', '17:00', '17:45'), ('{5}', '17:50', '18:35'),
             ('{6}', '09:45', '10:30'), ('{6}', '10:35', '11:20'), ('{6}', '11:25', '12:10'), ('{6}', '12:15', '13:00')) as f (giorni, inizio, fine);

create temporary table spazi_sito (codice text primary key, spazio uuid);
insert into spazi_sito
select 'C' || n, ('00000000-0000-0000-0000-0000000001' || lpad(n::text, 2, '0'))::uuid from generate_series(1, 6) n
union all select 'W' || n, ('00000000-0000-0000-0000-0000000001' || (10 + n)::text)::uuid from generate_series(1, 5) n
union all select 'P', '00000000-0000-0000-0000-0000000000c2';

create temporary table lezioni_sito as
select gen_random_uuid() as id, c.corso, g as giorno, p.inizio, p.fine, p.spazi, c.posti
from piano_sito p join corso_demo c on c.codice = p.codice cross join lateral unnest(p.giorni) g;

insert into lezione (id, corso_id, giorno_settimana, inizio, fine, valida_dal, posti)
select id, corso, giorno, inizio, fine, (select dal from stagione_corrente), posti from lezioni_sito;
insert into lezione_spazio (lezione_id, spazio_id)
select l.id, s.spazio from lezioni_sito l cross join lateral regexp_split_to_table(l.spazi, ' ') as k join spazi_sito s on s.codice = k;

-- ── Fasce orarie ────────────────────────────────────────────────────────────
-- Gli intervalli fino a un'ora dei corsi, per vasca e giorno, presi in ordine senza accavallamenti
do $$
declare
    f record;
    ultima_vasca uuid;
    ultimo_giorno int;
    ultima_fine time;
begin
    for f in
        select coalesce(s.padre_id, s.id) as vasca, l.giorno_settimana as giorno, l.inizio, l.fine, max(s.assetto) as assetto
        from lezione l join lezione_spazio ls on ls.lezione_id = l.id join spazio s on s.id = ls.spazio_id
        where l.fine - l.inizio <= interval '60 minutes'
        group by 1, 2, 3, 4
        order by 1, 2, 3, 4
    loop
        if f.vasca is distinct from ultima_vasca or f.giorno <> ultimo_giorno then
            ultima_fine := null;
        end if;
        if ultima_fine is null or f.inizio >= ultima_fine then
            insert into fascia_oraria (vasca_id, giorno_settimana, inizio, fine, assetto, valida_dal)
            values (f.vasca, f.giorno, f.inizio, f.fine, f.assetto, (select dal from stagione_corrente));
            ultima_fine := f.fine;
        end if;
        ultima_vasca := f.vasca;
        ultimo_giorno := f.giorno;
    end loop;
end $$;

-- ── Istruttori per lezione ──────────────────────────────────────────────────
-- Di base quello del corso; se a quell'ora ha già un'altra lezione, il primo libero tra quelli adatti.
create temporary table istruttori_demo (persona uuid, gruppo text, ordine int);
insert into istruttori_demo values
    ('00000000-0000-0000-0000-000000000108', 'piccoli', 1), ('00000000-0000-0000-0000-000000001101', 'piccoli', 2),
    ('00000000-0000-0000-0000-000000001109', 'piccoli', 3), ('00000000-0000-0000-0000-000000001111', 'piccoli', 4),
    ('00000000-0000-0000-0000-000000001105', 'scuola', 1), ('00000000-0000-0000-0000-000000001102', 'scuola', 2),
    ('00000000-0000-0000-0000-000000000107', 'scuola', 3), ('00000000-0000-0000-0000-000000000106', 'scuola', 4),
    ('00000000-0000-0000-0000-000000001103', 'scuola', 5), ('00000000-0000-0000-0000-000000000109', 'scuola', 6),
    ('00000000-0000-0000-0000-000000001104', 'scuola', 7), ('00000000-0000-0000-0000-000000001111', 'scuola', 8),
    ('00000000-0000-0000-0000-000000001106', 'tecnici', 1), ('00000000-0000-0000-0000-000000001104', 'tecnici', 2),
    ('00000000-0000-0000-0000-000000001110', 'tecnici', 3), ('00000000-0000-0000-0000-000000000109', 'tecnici', 4),
    ('00000000-0000-0000-0000-000000001107', 'adulti', 1), ('00000000-0000-0000-0000-000000001108', 'adulti', 2),
    ('00000000-0000-0000-0000-000000001112', 'adulti', 3), ('00000000-0000-0000-0000-000000000108', 'adulti', 4);

do $$
declare
    l record;
    scelto uuid;
begin
    create temporary table impegni (persona uuid, giorno int, inizio time, fine time);
    for l in
        select le.id, le.giorno_settimana as giorno, le.inizio, le.fine, c.istruttore_id as titolare,
               case when cd.codice in ('AB0', 'AB1', 'AB2', 'AP3', 'AP4', 'AA', 'LI') then 'piccoli'
                    when cd.codice in ('SB', 'SA', 'MJ', 'PR', 'PG', 'CR') then 'tecnici'
                    when cd.codice in ('EM', 'IN', 'AV', 'BA', 'AF') then 'adulti'
                    else 'scuola' end as gruppo
        from lezione le join corso c on c.id = le.corso_id join corso_demo cd on cd.corso = c.id
        where c.istruttore_id is not null
        order by le.giorno_settimana, le.inizio, cd.codice
    loop
        scelto := null;
        if not exists (select 1 from impegni i where i.persona = l.titolare and i.giorno = l.giorno and i.inizio < l.fine and l.inizio < i.fine) then
            scelto := l.titolare;
        else
            select d.persona into scelto
            from istruttori_demo d
            where not exists (select 1 from impegni i where i.persona = d.persona and i.giorno = l.giorno and i.inizio < l.fine and l.inizio < i.fine)
            order by (d.gruppo = l.gruppo) desc, d.ordine
            limit 1;
            if scelto is not null then
                update lezione set istruttore_id = scelto where id = l.id;
            end if;
        end if;
        if scelto is not null then
            insert into impegni values (scelto, l.giorno, l.inizio, l.fine);
        end if;
    end loop;
    drop table impegni;
end $$;

-- Luca, Giulia e Marta ora lavorano negli spogliatoi (V1016): l'evento di prova passa a un'istruttrice
update evento set istruttore_id = '00000000-0000-0000-0000-000000001105' where istruttore_id = '00000000-0000-0000-0000-000000000104';

-- ── Iscritti ────────────────────────────────────────────────────────────────
create temporary table nomi_nuovi (n int, nome text);
insert into nomi_nuovi select row_number() over (), x from unnest(array['Alessandro', 'Sofia', 'Leonardo', 'Giulia', 'Francesco', 'Aurora',
    'Lorenzo', 'Alice', 'Mattia', 'Ginevra', 'Andrea', 'Emma', 'Gabriele', 'Giorgia', 'Riccardo', 'Greta', 'Tommaso', 'Beatrice', 'Edoardo',
    'Anna', 'Marco', 'Chiara', 'Pietro', 'Ludovica', 'Filippo', 'Martina', 'Davide', 'Vittoria', 'Giovanni', 'Matilde', 'Samuele', 'Nicole',
    'Federico', 'Camilla', 'Luca', 'Rebecca', 'Diego', 'Elisa', 'Nicolò', 'Bianca', 'Christian', 'Arianna', 'Elia', 'Noemi', 'Jacopo',
    'Sara', 'Michele', 'Irene', 'Simone', 'Viola', 'Daniele', 'Carlotta', 'Enea', 'Mia', 'Achille', 'Adele', 'Cesare', 'Gaia', 'Ettore',
    'Asia', 'Giacomo', 'Caterina', 'Emanuele', 'Margherita', 'Raffaele', 'Benedetta', 'Paolo', 'Elena', 'Stefano', 'Laura', 'Massimo',
    'Valentina', 'Roberto', 'Silvia', 'Fabio', 'Paola', 'Alberto', 'Cristina', 'Claudio', 'Monica']) as x;
create temporary table cognomi_nuovi (n int, cognome text);
insert into cognomi_nuovi select row_number() over (), x from unnest(array['Rossi', 'Russo', 'Ferrari', 'Esposito', 'Bianco', 'Romano',
    'Colombo', 'Bruno', 'Marini', 'Gallo', 'Costa', 'Mancini', 'Giordano', 'Rizzo', 'Lombardi', 'Barbieri', 'Caruso', 'Mariani', 'Santoro',
    'Leone', 'Longo', 'Martini', 'Cattaneo', 'Bassi', 'Serra', 'Pellegrini', 'Fabbri', 'Testa', 'Sala', 'Riva', 'Gatti', 'Monti', 'Parisi',
    'Sartori', 'Vitale', 'Orlando', 'Ferraro', 'Silvestri', 'Grassi', 'Benedetti', 'Brambilla', 'Villa', 'Crippa', 'Fumagalli', 'Mauri',
    'Galli', 'Rota', 'Pozzi', 'Bonfanti', 'Cereda', 'Mapelli', 'Ronchi', 'Arosio', 'Tagliabue', 'Bianchi', 'Moretti', 'Conti', 'Greco',
    'De Luca', 'Marchi', 'Coppola', 'Ferri', 'Fontana', 'Ricci', 'Valsecchi', 'Mazza', 'Cantù', 'Perego', 'Sormani', 'Zappa']) as x;

do $$
declare
    s record;
    l record;
    v record;
    nuova uuid;
    persona_nuova uuid;
    mancano int;
    contatore int := 0;
    eta_nuova int;
    nati int;
    giorni int;
    n_nomi int := (select count(*) from nomi_nuovi);
    n_cognomi int := (select count(*) from cognomi_nuovi);
begin
    select * into s from stagione_corrente;

    -- 1. chi era già iscritto resta nel suo corso (o nel gruppo d'età giusto dello stesso corso), nel turno più vicino
    for v in
        select *, case
                when corso in ('00000000-0000-0000-0000-000000000611', '00000000-0000-0000-0000-000000000626', '00000000-0000-0000-0000-000000000627') then
                    case when eta <= 0 then '00000000-0000-0000-0000-000000000611' when eta = 1 then '00000000-0000-0000-0000-000000000626'
                         else '00000000-0000-0000-0000-000000000627' end::uuid
                when corso in ('00000000-0000-0000-0000-000000000612', '00000000-0000-0000-0000-000000000628') then
                    case when eta <= 3 then '00000000-0000-0000-0000-000000000628' else '00000000-0000-0000-0000-000000000612' end::uuid
                when corso in ('00000000-0000-0000-0000-000000000622', '00000000-0000-0000-0000-000000000629') then
                    case when eta <= 12 then '00000000-0000-0000-0000-000000000622' else '00000000-0000-0000-0000-000000000629' end::uuid
                else corso end as corso_nuovo
        from vecchie_iscrizioni
        order by fissa desc, persona, giorno, inizio
    loop
        select nl.id into nuova from lezione nl
        where nl.corso_id = v.corso_nuovo
          and not exists (select 1 from iscrizione i where i.lezione_id = nl.id and i.persona_id = v.persona)
          and not exists (select 1 from iscrizione i join lezione x on x.id = i.lezione_id
                          where i.persona_id = v.persona and x.giorno_settimana = nl.giorno_settimana and x.inizio < nl.fine and nl.inizio < x.fine)
          and (v.fissa or nl.posti is null
               or (select count(*) from iscrizione i where i.lezione_id = nl.id and (i.al is null or i.al >= current_date)) < nl.posti)
        order by (nl.giorno_settimana = v.giorno) desc, abs(extract(epoch from nl.inizio - v.inizio)), nl.giorno_settimana
        limit 1;
        if nuova is not null then
            insert into iscrizione (lezione_id, persona_id, dal, al) values (nuova, v.persona, v.dal, v.al);
            if v.corso_nuovo <> v.corso then
                update abbonamento set corso_id = v.corso_nuovo where persona_id = v.persona and corso_id = v.corso and tipo = 'CORSO';
            end if;
        end if;
    end loop;

    -- 2. le attese tornano sul turno corrispondente
    insert into attesa (lezione_id, persona_id, aggiunta_il, aggiunta_da, note, avvisata_il, stato, chiusa_il)
    select (select nl.id from lezione nl where nl.corso_id = a.corso
            order by (nl.giorno_settimana = a.giorno) desc, abs(extract(epoch from nl.inizio - a.inizio)) limit 1),
           a.persona, a.aggiunta_il, a.aggiunta_da, a.note, a.avvisata_il, a.stato, a.chiusa_il
    from vecchie_attese a;

    -- 3. i posti liberi: un po' di bisettimanali, poi chi non fa ancora corsi, poi persone nuove dell'età giusta.
    --    I turni con qualcuno in attesa si riempiono del tutto; gli altri restano con 0-3 posti liberi.
    for l in
        select nl.id, nl.corso_id, nl.giorno_settimana as giorno, nl.inizio, nl.fine, nl.posti, cd.eta_da, cd.eta_a,
               case when exists (select 1 from attesa a where a.lezione_id = nl.id and a.stato = 'IN_ATTESA') then nl.posti
                    else nl.posti - (abs(hashtext(cd.codice || nl.giorno_settimana || nl.inizio::text)) % 4) end as obiettivo
        from lezione nl join corso_demo cd on cd.corso = nl.corso_id
        where nl.posti is not null
        order by nl.giorno_settimana, nl.inizio, cd.codice
    loop
        mancano := l.obiettivo - (select count(*) from iscrizione i where i.lezione_id = l.id and (i.al is null or i.al >= current_date));
        continue when mancano <= 0;

        -- a) bisettimanali: chi fa già lo stesso corso un altro giorno (circa uno su quattro dei posti da riempire)
        insert into iscrizione (lezione_id, persona_id, dal)
        select l.id, c.persona_id, s.dal
        from (select i.persona_id from iscrizione i join lezione x on x.id = i.lezione_id
              where x.corso_id = l.corso_id and x.giorno_settimana <> l.giorno
                and i.persona_id::text not like '00000000-0000-0000-0000-%'
                and (select count(*) from iscrizione j where j.persona_id = i.persona_id) = 1
                and not exists (select 1 from iscrizione j join lezione y on y.id = j.lezione_id
                                where j.persona_id = i.persona_id and y.giorno_settimana = l.giorno)
              order by md5(i.persona_id::text || l.id::text)
              limit mancano / 4) c;
        mancano := l.obiettivo - (select count(*) from iscrizione i where i.lezione_id = l.id and (i.al is null or i.al >= current_date));
        continue when mancano <= 0;

        -- b) persone demo dell'età giusta che non fanno corsi e non hanno abbonamenti
        insert into iscrizione (lezione_id, persona_id, dal)
        select l.id, p.id, s.dal
        from persona p
        where p.data_nascita is not null
          and s.anno - extract(year from p.data_nascita)::int between l.eta_da and l.eta_a
          and p.id::text not like '00000000-0000-0000-0000-%'
          and not exists (select 1 from account a where a.persona_id = p.id)
          and not exists (select 1 from iscrizione i where i.persona_id = p.id)
          and not exists (select 1 from abbonamento b where b.persona_id = p.id)
        order by md5(p.id::text)
        limit mancano;
        mancano := l.obiettivo - (select count(*) from iscrizione i where i.lezione_id = l.id and (i.al is null or i.al >= current_date));

        -- c) persone nuove
        for k in 1 .. greatest(mancano, 0) loop
            contatore := contatore + 1;
            eta_nuova := l.eta_da + (contatore * 7) % (l.eta_a - l.eta_da + 1);
            nati := s.anno - eta_nuova;
            giorni := case when nati = extract(year from current_date)::int
                           then greatest((current_date - 120) - make_date(nati, 1, 1), 1) else 365 end;
            persona_nuova := gen_random_uuid();
            insert into persona (id, nome, cognome, data_nascita)
            values (persona_nuova,
                    (select nome from nomi_nuovi where n = (contatore * 7) % n_nomi + 1),
                    (select cognome from cognomi_nuovi where n = (contatore * 13 + contatore / n_cognomi) % n_cognomi + 1),
                    make_date(nati, 1, 1) + (abs(hashtext(persona_nuova::text)) % giorni));
            insert into iscrizione (lezione_id, persona_id, dal) values (l.id, persona_nuova, s.dal);
        end loop;
    end loop;
end $$;

-- ── Quello che serve a chi è iscritto (come in V1006) ───────────────────────
create temporary table iscritti_demo as
select p.id, abs(hashtext(p.id::text)) as h,
       (select anno from stagione_corrente) - extract(year from p.data_nascita)::int as eta
from persona p
where p.id::text not like '00000000-0000-0000-0000-%'
  and exists (select 1 from iscrizione i where i.persona_id = p.id);

insert into braccialetto (codice, persona_id, assegnato_il, stato_dal)
select upper(substr(md5(id::text), 1, 8)), id, now() - (h % 700) * interval '1 day', now() - (h % 700) * interval '1 day'
from iscritti_demo c where not exists (select 1 from braccialetto b where b.persona_id = c.id);

-- certificati: uno su venti scaduto; i più piccoli dell'Acquababy non ne hanno bisogno
insert into certificato_medico (persona_id, tipo, rilasciato_il, scadenza)
select id, case when eta >= 14 and h % 7 = 0 then 'AGONISTICO' else 'NON_AGONISTICO' end,
       case when h % 20 = 0 then current_date - (h % 45) - 366 else current_date - (h % 300) - 30 end,
       case when h % 20 = 0 then current_date - (h % 45) - 1 else current_date + (h % 330) + 5 end
from iscritti_demo c where eta >= 3 and not exists (select 1 from certificato_medico m where m.persona_id = c.id);

-- tesseramento: uno su venticinque non l'ha pagato
insert into tesseramento (persona_id, stagione, dal, al, importo, pagato_il)
select c.id, s.nome, s.dal, s.al, 35, s.dal + (c.h % 40)
from iscritti_demo c, stagione_corrente s
where c.h % 25 <> 1 and not exists (select 1 from tesseramento t where t.persona_id = c.id and t.stagione = s.nome);

-- abbonamento per ogni corso frequentato, con le volte a settimana dei turni; uno su trenta scaduto
insert into abbonamento (persona_id, tipo, corso_id, volte_a_settimana, dal, al)
select i.persona_id, 'CORSO', l.corso_id, least(count(*), 7), s.dal,
       case when max(c.h) % 30 = 2 then current_date - (max(c.h) % 20) - 1 else s.al end
from iscrizione i
join lezione l on l.id = i.lezione_id
join iscritti_demo c on c.id = i.persona_id
cross join stagione_corrente s
where not exists (select 1 from abbonamento b where b.persona_id = i.persona_id and b.corso_id = l.corso_id)
group by i.persona_id, l.corso_id, s.dal, s.al;

-- chi fa i corsi leoniani è un alunno dell'Istituto
insert into appartenenza_gruppo (gruppo_id, persona_id, dal)
select distinct '00000000-0000-0000-0000-000000000901'::uuid, i.persona_id, s.dal
from iscrizione i join lezione l on l.id = i.lezione_id cross join stagione_corrente s
where l.corso_id in ('00000000-0000-0000-0000-000000000631', '00000000-0000-0000-0000-000000000632', '00000000-0000-0000-0000-000000000633')
  and not exists (select 1 from appartenenza_gruppo g where g.persona_id = i.persona_id and g.gruppo_id = '00000000-0000-0000-0000-000000000901' and g.al is null);

-- ── Listino: i corsi nuovi nelle offerte ────────────────────────────────────
insert into offerta_corso (offerta_id, corso_id)
select o.id, c.corso::uuid
from offerta o join (values
    ('Acqua Baby (15 lezioni)', '00000000-0000-0000-0000-000000000626'), ('Acqua Baby (15 lezioni)', '00000000-0000-0000-0000-000000000627'),
    ('Ambientamento', '00000000-0000-0000-0000-000000000628'),
    ('Propaganda FIN', '00000000-0000-0000-0000-000000000629'),
    ('Scuola nuoto e corsi tecnici', '00000000-0000-0000-0000-000000000630')
) as c (offerta, corso) on c.offerta = o.nome;
update offerta set nome = 'Acquababy (15 lezioni)' where nome = 'Acqua Baby (15 lezioni)';

create temporary table stagioni_listino as
select s.id, s.nome from stagione s where exists (select 1 from offerta o where o.stagione_id = s.id);
insert into offerta (id, stagione_id, nome, tipo, riduzione_tardiva, ordine)
select md5(s.nome || o.chiave)::uuid, s.id, o.nome, 'CORSO', true, o.ordine
from stagioni_listino s, (values ('leoniani', 'Corsi leoniani', 15), ('adulti', 'Nuoto adulti', 45)) as o (chiave, nome, ordine);
insert into offerta_corso (offerta_id, corso_id)
select md5(s.nome || c.chiave)::uuid, c.corso::uuid
from stagioni_listino s, (values
    ('leoniani', '00000000-0000-0000-0000-000000000631'), ('leoniani', '00000000-0000-0000-0000-000000000632'),
    ('leoniani', '00000000-0000-0000-0000-000000000633'),
    ('adulti', '00000000-0000-0000-0000-000000000634'), ('adulti', '00000000-0000-0000-0000-000000000635'),
    ('adulti', '00000000-0000-0000-0000-000000000636'), ('adulti', '00000000-0000-0000-0000-000000000637')
) as c (chiave, corso);
insert into offerta_prezzo (offerta_id, volte, gruppo_id, importo)
select md5(s.nome || p.chiave)::uuid, p.volte, p.gruppo::uuid, p.importo
from stagioni_listino s, (values
    ('leoniani', 1, null, 510.00), ('leoniani', 1, '00000000-0000-0000-0000-000000000901', 460.00),
    ('adulti', 1, null, 520.00), ('adulti', 2, null, 795.00), ('adulti', 3, null, 950.00)
) as p (chiave, volte, gruppo, importo);

drop table stagioni_listino;
drop table iscritti_demo;
drop table cognomi_nuovi;
drop table nomi_nuovi;
drop table istruttori_demo;
drop table lezioni_sito;
drop table spazi_sito;
drop table piano_sito;
drop table corso_demo;
drop table vecchie_attese;
drop table vecchie_iscrizioni;
drop table stagione_corrente;
