-- La società demo, le sue stagioni (dal 1/9 al 31/8, quota 35 €) e due gruppi di clienti, come sul sito della Leone
-- XIII Sport. Persone inventate.
update societa set nome = 'Leone XIII Sport', ragione_sociale = 'Leone XIII Sport S.S.D. a r.l.', citta = 'Milano', provincia = 'MI',
    email = 'segreteria@example.org', giorni_anticipo = 30;

-- la stagione scorsa, quella in corso e la prossima, calcolate su oggi
insert into stagione (nome, dal, al, quota)
select a || '/' || lpad(((a + 1) % 100)::text, 2, '0'), make_date(a, 9, 1), make_date(a + 1, 8, 31), 35.00
from (select case when extract(month from current_date) >= 9 then extract(year from current_date)::int
                  else extract(year from current_date)::int - 1 end + d as a
      from generate_series(-1, 1) d) s;

-- i tesseramenti demo seguono le date della loro stagione
update tesseramento t set dal = s.dal, al = s.al from stagione s where t.stagione = s.nome;

insert into gruppo_clienti (id, nome, descrizione) values
    ('00000000-0000-0000-0000-000000000901', 'Alunni dell''Istituto', 'Studenti della scuola: prezzi dedicati e precedenza nei rinnovi'),
    ('00000000-0000-0000-0000-000000000902', 'Squadra agonistica', 'Atleti tesserati per le gare');

insert into appartenenza_gruppo (gruppo_id, persona_id, dal) values
    ('00000000-0000-0000-0000-000000000901', '00000000-0000-0000-0000-000000000502', current_date - 60),
    ('00000000-0000-0000-0000-000000000901', '00000000-0000-0000-0000-000000000522', current_date - 60),
    ('00000000-0000-0000-0000-000000000901', '00000000-0000-0000-0000-000000000503', current_date - 300),
    ('00000000-0000-0000-0000-000000000902', '00000000-0000-0000-0000-000000000544', current_date - 15),
    ('00000000-0000-0000-0000-000000000902', '00000000-0000-0000-0000-000000000512', current_date - 100);
