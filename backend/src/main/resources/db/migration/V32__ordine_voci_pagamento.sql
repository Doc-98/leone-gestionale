-- L'ordine delle voci di un pagamento è quello scelto da chi lo registra: lo salviamo (prima dipendeva dal database).
-- Hibernate scrive la posizione (0, 1, 2, ...) a ogni inserimento; per le righe già presenti si parte dall'ordine di
-- creazione dell'addebito, poi dall'id, così l'ordine è stabile.
alter table pagamento_addebito add column posizione integer;

update pagamento_addebito pa
set posizione = o.pos
from (
    select x.pagamento_id, x.addebito_id,
           row_number() over (partition by x.pagamento_id order by a.creato_il, x.addebito_id) - 1 as pos
    from pagamento_addebito x
    join addebito a on a.id = x.addebito_id
) o
where pa.pagamento_id = o.pagamento_id and pa.addebito_id = o.addebito_id;

-- Chi inserisce senza indicare la posizione (i dati demo, che girano dopo e non si possono cambiare) la riceve da qui:
-- la prima libera del pagamento (0, 1, 2, ...), senza buchi, come la vuole Hibernate. Il codice dell'applicazione
-- indica sempre la posizione.
create function pagamento_addebito_posizione() returns trigger language plpgsql as $$
begin
    if new.posizione is null then
        select coalesce(max(posizione) + 1, 0) into new.posizione from pagamento_addebito where pagamento_id = new.pagamento_id;
    end if;
    return new;
end $$;
create trigger pagamento_addebito_posizione before insert on pagamento_addebito
    for each row execute function pagamento_addebito_posizione();

alter table pagamento_addebito alter column posizione set not null;
