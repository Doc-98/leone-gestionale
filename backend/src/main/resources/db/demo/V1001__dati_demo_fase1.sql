-- Dati demo della fase 1: modello settimanale, tariffe, qualche turno scoperto e qualche richiesta.

insert into modello_settimanale (id, impianto_id, nome, valido_dal) values
    ('00000000-0000-0000-0000-000000000501', '00000000-0000-0000-0000-0000000000b1', 'Stagione 2026-27',
     date_trunc('year', current_date)::date);

-- Lo stesso giro dei turni demo già presenti, più il venerdì dello spogliatoio piccoli ancora da assegnare
insert into modello_riga (modello_id, postazione_id, giorno_settimana, inizio, fine, persona_id) values
    ('00000000-0000-0000-0000-000000000501', '00000000-0000-0000-0000-000000000402', 1, '15:00', '20:00', '00000000-0000-0000-0000-000000000103'),
    ('00000000-0000-0000-0000-000000000501', '00000000-0000-0000-0000-000000000405', 3, '15:45', '19:15', '00000000-0000-0000-0000-000000000103'),
    ('00000000-0000-0000-0000-000000000501', '00000000-0000-0000-0000-000000000401', 6, '09:30', '13:15', '00000000-0000-0000-0000-000000000103'),
    ('00000000-0000-0000-0000-000000000501', '00000000-0000-0000-0000-000000000404', 2, '15:30', '19:15', '00000000-0000-0000-0000-000000000104'),
    ('00000000-0000-0000-0000-000000000501', '00000000-0000-0000-0000-000000000404', 4, '15:30', '19:15', '00000000-0000-0000-0000-000000000104'),
    ('00000000-0000-0000-0000-000000000501', '00000000-0000-0000-0000-000000000403', 1, '15:00', '20:00', '00000000-0000-0000-0000-000000000105'),
    ('00000000-0000-0000-0000-000000000501', '00000000-0000-0000-0000-000000000405', 5, '15:45', '19:15', '00000000-0000-0000-0000-000000000105'),
    ('00000000-0000-0000-0000-000000000501', '00000000-0000-0000-0000-000000000405', 6, '09:00', '13:00', '00000000-0000-0000-0000-000000000102'),
    ('00000000-0000-0000-0000-000000000501', '00000000-0000-0000-0000-000000000404', 5, '15:30', '19:15', null);

insert into tariffa (mansione_id, persona_id, euro_ora, dal) values
    ('00000000-0000-0000-0000-000000000301', null, 9.00, '2026-01-01'),
    ('00000000-0000-0000-0000-000000000302', null, 9.50, '2026-01-01'),
    -- eccezione: il coordinatore quando fa l'atrio
    ('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000102', 11.00, '2026-01-01');

-- Turni scoperti: il venerdì dello spogliatoio piccoli, dalla settimana prossima per quattro settimane
insert into turno (persona_id, postazione_id, data, inizio, fine)
select null, '00000000-0000-0000-0000-000000000404', d::date, '15:30', '19:15'
from generate_series(current_date + 1, current_date + 28, interval '1 day') as g(d)
where extract(isodow from d) = 5;

-- Una richiesta di sostituzione aperta (Giulia non può fare il prossimo martedì)
insert into richiesta_turno (tipo, turno_id, richiedente_id, messaggio, stato)
select 'SOSTITUZIONE', t.id, t.persona_id, 'Ho un esame, qualcuno può coprirmi?', 'IN_ATTESA_COLLEGA'
from turno t
where t.persona_id = '00000000-0000-0000-0000-000000000104'
  and t.data > current_date and extract(isodow from t.data) = 2
order by t.data limit 1;

-- Una richiesta di cambio orario già pronta per l'approvazione (Marta, prossimo venerdì)
insert into richiesta_turno (tipo, turno_id, richiedente_id, nuovo_inizio, nuovo_fine, messaggio, stato)
select 'CAMBIO_ORARIO', t.id, t.persona_id, '16:00', '19:15', 'Esco tardi da lezione', 'IN_ATTESA_APPROVAZIONE'
from turno t
where t.persona_id = '00000000-0000-0000-0000-000000000105'
  and t.data > current_date and extract(isodow from t.data) = 5
order by t.data limit 1;
