-- Una macchina e un servizio si eliminano sempre, anche se sono stati usati (ADR 0030): ogni utilizzo conserva una COPIA
-- del nome della macchina e del servizio presa alla registrazione, e lo storico legge la copia. Il riferimento resta, ma
-- diventa facoltativo: se la macchina o il servizio si cancella, il riferimento si svuota e la copia resta.
-- Un servizio eliminato lascia le sue macchine senza servizio: rispondono «Macchina non configurata» finché non ne hanno uno.

alter table utilizzo
    add column dispositivo_nome varchar(80),
    add column servizio_nome    varchar(60);

-- Le righe già presenti: la copia è il nome com'è adesso.
update utilizzo u set dispositivo_nome = d.nome from dispositivo d where d.id = u.dispositivo_id;
update utilizzo u set servizio_nome = s.nome from servizio s where s.id = u.servizio_id;

-- Chi inserisce senza la copia (i dati demo, che girano dopo e non si possono cambiare) la riceve da qui. Il codice
-- dell'applicazione scrive sempre la copia.
create function copia_macchina_e_servizio() returns trigger language plpgsql as $$
begin
    if new.dispositivo_nome is null and new.dispositivo_id is not null then
        select d.nome into new.dispositivo_nome from dispositivo d where d.id = new.dispositivo_id;
    end if;
    if new.servizio_nome is null and new.servizio_id is not null then
        select s.nome into new.servizio_nome from servizio s where s.id = new.servizio_id;
    end if;
    return new;
end $$;
create trigger utilizzo_copia before insert on utilizzo
    for each row execute function copia_macchina_e_servizio();

alter table utilizzo alter column dispositivo_nome set not null;

-- Il riferimento alla macchina diventa facoltativo e si svuota se la macchina viene eliminata.
alter table utilizzo alter column dispositivo_id drop not null;
alter table utilizzo drop constraint utilizzo_dispositivo_id_fkey;
alter table utilizzo add constraint utilizzo_dispositivo_id_fkey
    foreign key (dispositivo_id) references dispositivo (id) on delete set null;

-- Idem per il servizio.
alter table utilizzo drop constraint utilizzo_servizio_id_fkey;
alter table utilizzo add constraint utilizzo_servizio_id_fkey
    foreign key (servizio_id) references servizio (id) on delete set null;

-- Una macchina senza servizio (il suo servizio è stato eliminato): non scala crediti e non lascia partire nessuno.
alter table dispositivo drop constraint dispositivo_check;
alter table dispositivo drop constraint dispositivo_servizio_id_fkey;
alter table dispositivo add constraint dispositivo_servizio_id_fkey
    foreign key (servizio_id) references servizio (id) on delete set null;
