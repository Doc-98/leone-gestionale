-- Il listino demo della stagione in corso e della prossima, con i prezzi e le regole del sito della Leone XIII Sport
-- (scuola nuoto mono 520 €, bi 795 €; alunni dell'Istituto 460 €; nuoto libero e carnet; riduzione tardiva, rate,
-- recesso, sospensione). Il gruppo «Alunni dell'Istituto» è in V1013.
create temporary table stagioni_listino as
select id, nome, extract(year from dal)::int as a from stagione where al >= current_date;

create temporary table offerte_demo (chiave text, nome text, tipo text, ingressi int, mesi int, giorni int, corso uuid, riduzione boolean, ordine int, corsi uuid[]);
insert into offerte_demo values
    ('scuola', 'Scuola nuoto e corsi tecnici', 'CORSO', null, null, null, null, true, 10,
     array['00000000-0000-0000-0000-000000000614', '00000000-0000-0000-0000-000000000615', '00000000-0000-0000-0000-000000000616',
           '00000000-0000-0000-0000-000000000617', '00000000-0000-0000-0000-000000000618', '00000000-0000-0000-0000-000000000619',
           '00000000-0000-0000-0000-000000000620', '00000000-0000-0000-0000-000000000621', '00000000-0000-0000-0000-000000000623']::uuid[]),
    ('ambientamento', 'Ambientamento', 'CORSO', null, null, null, null, true, 20,
     array['00000000-0000-0000-0000-000000000612', '00000000-0000-0000-0000-000000000613']::uuid[]),
    ('acquababy', 'Acqua Baby (15 lezioni)', 'CORSO', null, null, null, null, false, 30, array['00000000-0000-0000-0000-000000000611']::uuid[]),
    ('propaganda', 'Propaganda FIN', 'CORSO', null, null, null, null, true, 40, array['00000000-0000-0000-0000-000000000622']::uuid[]),
    ('aquafitness', 'AquaFitness', 'CORSO', null, null, null, null, true, 50, array['00000000-0000-0000-0000-000000000624']::uuid[]),
    ('liberoannuale', 'Nuoto libero annuale', 'CORSO', null, null, null, null, false, 60, array['00000000-0000-0000-0000-000000000625']::uuid[]),
    ('libero5', 'Nuoto libero 5 ingressi', 'CARNET', 5, 2, null, '00000000-0000-0000-0000-000000000625', false, 70, null),
    ('libero10', 'Nuoto libero 10 ingressi', 'CARNET', 10, 3, null, '00000000-0000-0000-0000-000000000625', false, 71, null),
    ('libero20', 'Nuoto libero 20 ingressi', 'CARNET', 20, 6, null, '00000000-0000-0000-0000-000000000625', false, 72, null),
    ('extra', 'Ingresso extra', 'CARNET', 1, null, 1, null, false, 80, null),
    ('agonistica', 'Quota squadra agonistica', 'ALTRO', null, null, null, null, false, 90, null);

insert into offerta (id, stagione_id, nome, tipo, ingressi, validita_mesi, validita_giorni, corso_id, riduzione_tardiva, ordine)
select md5(s.nome || o.chiave)::uuid, s.id, o.nome, o.tipo, o.ingressi, o.mesi, o.giorni, o.corso, o.riduzione, o.ordine
from stagioni_listino s, offerte_demo o;

insert into offerta_corso (offerta_id, corso_id)
select md5(s.nome || o.chiave)::uuid, unnest(o.corsi) from stagioni_listino s, offerte_demo o where o.corsi is not null;

insert into offerta_prezzo (offerta_id, volte, gruppo_id, importo)
select md5(s.nome || p.chiave)::uuid, p.volte, p.gruppo::uuid, p.importo
from stagioni_listino s, (values
    ('scuola', 1, null, 520.00), ('scuola', 2, null, 795.00), ('scuola', 1, '00000000-0000-0000-0000-000000000901', 460.00),
    ('ambientamento', 1, null, 520.00), ('ambientamento', 2, null, 795.00),
    ('acquababy', null, null, 300.00),
    ('propaganda', 1, null, 720.00),
    ('aquafitness', 1, null, 450.00), ('aquafitness', 2, null, 770.00), ('aquafitness', 0, null, 1150.00),
    ('liberoannuale', 1, null, 390.00), ('liberoannuale', 2, null, 610.00), ('liberoannuale', 3, null, 780.00), ('liberoannuale', 0, null, 1000.00),
    ('libero5', null, null, 70.00), ('libero10', null, null, 130.00), ('libero20', null, null, 240.00),
    ('extra', null, null, 5.00),
    ('agonistica', null, null, 50.00)
) as p (chiave, volte, gruppo, importo);

insert into regola_prezzo (stagione_id, tipo, attiva, parametri)
select s.id, r.tipo, true, r.parametri
from stagioni_listino s, lateral (values
    ('RIDUZIONE_TARDIVA', jsonb_build_object('scaglioni', jsonb_build_array(
        jsonb_build_object('dal', make_date(s.a, 11, 1), 'percentuale', 10), jsonb_build_object('dal', make_date(s.a, 12, 1), 'percentuale', 20),
        jsonb_build_object('dal', make_date(s.a + 1, 1, 1), 'percentuale', 30), jsonb_build_object('dal', make_date(s.a + 1, 2, 1), 'percentuale', 40)))),
    ('RATE', jsonb_build_object('numero', 2, 'primaPercentuale', 50, 'scadenze', jsonb_build_array(make_date(s.a, 9, 14)))),
    ('RECESSO', jsonb_build_object('nelBorsellino', true, 'scaglioni', jsonb_build_array(
        jsonb_build_object('finoAl', make_date(s.a, 7, 31), 'percentuale', 100), jsonb_build_object('finoAl', make_date(s.a, 9, 13), 'percentuale', 90),
        jsonb_build_object('finoAl', make_date(s.a, 10, 31), 'percentuale', 60), jsonb_build_object('finoAl', make_date(s.a, 12, 31), 'percentuale', 40)))),
    ('SOSPENSIONE', jsonb_build_object('settimaneMinime', 6, 'settimaneCertificato', 4))
) as r (tipo, parametri);

drop table offerte_demo;
drop table stagioni_listino;
