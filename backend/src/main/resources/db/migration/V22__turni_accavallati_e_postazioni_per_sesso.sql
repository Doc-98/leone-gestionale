-- Gestione turni (ADR 0023).
-- Due turni accavallati della stessa persona non sono più vietati dal database: il programma avvisa e il
-- coordinatore può confermare (es. un passaggio di consegne). Resta vietato, nel servizio, avere due persone
-- sulla stessa postazione nello stesso orario.
alter table turno drop constraint turno_senza_sovrapposizioni;

-- Postazione riservata a un sesso (es. lo spogliatoio femminile): null = chiunque.
alter table postazione add column solo_sesso varchar(1) check (solo_sesso in ('M', 'F'));

create index turno_postazione_giorno on turno (postazione_id, data) where stato = 'PIANIFICATO';
