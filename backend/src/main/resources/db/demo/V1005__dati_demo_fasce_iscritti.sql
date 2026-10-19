-- Fasce orarie, posti per turno e iscritti demo. Tutte persone inventate (nomi e cognomi comuni combinati a caso).

-- ── Segreteria ──────────────────────────────────────────────────────────────
insert into persona (id, nome, cognome, email) values
    ('00000000-0000-0000-0000-000000000110', 'Anna', 'Villa', 'segreteria@demo.leone');
insert into account (id, persona_id, email, password_hash) values
    ('00000000-0000-0000-0000-000000000210', '00000000-0000-0000-0000-000000000110', 'segreteria@demo.leone',
     '{bcrypt}$2a$10$QDF4Rqled7UdqqJMA/RyVuJ77K5utaMoZgNvPgOuQvUCzoUGoIw4e');
insert into assegnazione_ruolo (account_id, ruolo, impianto_id) values
    ('00000000-0000-0000-0000-000000000210', 'SEGRETERIA', '00000000-0000-0000-0000-0000000000b1');

-- ── Fasce: gli orari dei turni fino a un'ora, per vasca e giorno ────────────
insert into fascia_oraria (vasca_id, giorno_settimana, inizio, fine, assetto, valida_dal)
select coalesce(s.padre_id, s.id), l.giorno_settimana, l.inizio, l.fine, max(s.assetto), date_trunc('year', current_date)::date
from lezione l
join lezione_spazio ls on ls.lezione_id = l.id
join spazio s on s.id = ls.spazio_id
where l.fine - l.inizio <= interval '60 minutes'
group by 1, 2, 3, 4;
-- la vasca grande usata intera (es. nuoto libero del sabato) è a 6 corsie
update fascia_oraria set assetto = 6 where vasca_id = '00000000-0000-0000-0000-0000000000c1' and assetto is null;
-- il mercoledì comincia la Propaganda alle 14:30, con la vasca a 5 corsie
insert into fascia_oraria (vasca_id, giorno_settimana, inizio, fine, assetto, valida_dal) values
    ('00000000-0000-0000-0000-0000000000c1', 3, '14:30', '15:15', 5, date_trunc('year', current_date)::date);

-- ── Posti per turno ─────────────────────────────────────────────────────────
update lezione l set posti = case c.tipo
        when 'ACQUATICITA' then 6
        when 'SCUOLA_NUOTO' then 8
        when 'AGONISMO' then 14
        when 'MASTER' then 10
        when 'ACQUAGYM' then 16
        else null end
from corso c where c.id = l.corso_id;

-- ── Iscritti inventati ──────────────────────────────────────────────────────
create temporary table nomi_demo (n int, nome text);
insert into nomi_demo
select row_number() over (), x from unnest(array['Alessandro', 'Sofia', 'Leonardo', 'Giulia', 'Francesco', 'Aurora', 'Lorenzo', 'Alice',
    'Mattia', 'Ginevra', 'Andrea', 'Emma', 'Gabriele', 'Giorgia', 'Riccardo', 'Greta', 'Tommaso', 'Beatrice', 'Edoardo', 'Anna',
    'Marco', 'Chiara', 'Pietro', 'Ludovica', 'Filippo', 'Martina', 'Davide', 'Vittoria', 'Giovanni', 'Matilde', 'Samuele', 'Nicole',
    'Federico', 'Camilla', 'Luca', 'Rebecca', 'Diego', 'Elisa', 'Nicolò', 'Bianca']) as x;
create temporary table cognomi_demo (n int, cognome text);
insert into cognomi_demo
select row_number() over (), x from unnest(array['Rossi', 'Russo', 'Ferrari', 'Esposito', 'Bianco', 'Romano', 'Colombo', 'Bruno',
    'Marini', 'Gallo', 'Costa', 'Mancini', 'Giordano', 'Rizzo', 'Lombardi', 'Barbieri', 'Caruso', 'Mariani', 'Santoro', 'Leone',
    'Longo', 'Martini', 'Cattaneo', 'Bassi', 'Serra', 'Pellegrini', 'Fabbri', 'Testa', 'Sala', 'Riva', 'Gatti', 'Monti', 'Parisi',
    'Sartori', 'Vitale', 'Orlando', 'Ferraro', 'Silvestri', 'Grassi', 'Benedetti']) as x;

-- 600 persone: 420 tra 0 e 16 anni, 180 adulti
create temporary table clienti_demo as
select gen_random_uuid() as id, n.nome, c.cognome,
       case when i <= 420 then current_date - ((i * 137) % (16 * 365) + 60)
            else current_date - (16 * 365 + (i * 211) % (50 * 365)) end as data_nascita
from generate_series(1, 600) as i
join nomi_demo n on n.n = (i * 7) % 40 + 1
join cognomi_demo c on c.n = (i * 13 + i / 40) % 40 + 1;

insert into persona (id, nome, cognome, data_nascita) select id, nome, cognome, data_nascita from clienti_demo;

-- Ogni turno con dei posti prende persone dell'età giusta, fino a riempirlo quasi tutto (qualcuno al completo)
insert into iscrizione (lezione_id, persona_id, dal)
select e.lezione, e.persona, date_trunc('year', current_date)::date
from (
    select l.id as lezione, p.id as persona, l.posti,
           row_number() over (partition by l.id order by md5(l.id::text || p.id::text)) as posizione
    from lezione l
    join corso c on c.id = l.corso_id
    join clienti_demo p on extract(year from age(current_date, p.data_nascita)) between coalesce(c.eta_da, 0) and coalesce(c.eta_a, 120)
    where l.posti is not null
) e
where e.posizione <= e.posti - (abs(hashtext(e.lezione::text)) % 4);

drop table clienti_demo;
drop table cognomi_demo;
drop table nomi_demo;
