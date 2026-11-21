-- Un metodo di pagamento si può eliminare (anche se è stato usato): ogni pagamento e ogni uscita conserva una COPIA del
-- metodo presa alla registrazione (nome e proprietà che servono a storico e conti), e lo storico legge la copia.
-- Il riferimento al metodo resta, ma diventa facoltativo: se il metodo si cancella, il riferimento si svuota e la copia resta.
-- (Il borsellino come metodo non si elimina mai; i movimenti del borsellino non puntano a un metodo, quindi non cambiano.)

alter table pagamento_metodo
    add column metodo_nome          varchar(60),
    add column metodo_contanti      boolean,
    add column metodo_da_incassare  boolean,
    add column metodo_riscontro     boolean,
    add column metodo_borsellino    boolean;

alter table uscita
    add column metodo_nome          varchar(60),
    add column metodo_contanti      boolean,
    add column metodo_da_incassare  boolean,
    add column metodo_riscontro     boolean,
    add column metodo_borsellino    boolean;

-- Le righe già presenti: la copia è il metodo com'è adesso.
update pagamento_metodo pm
set metodo_nome = m.nome, metodo_contanti = m.contanti, metodo_da_incassare = m.da_incassare,
    metodo_riscontro = m.riscontro, metodo_borsellino = m.borsellino
from metodo_pagamento m
where m.id = pm.metodo_id;

update uscita u
set metodo_nome = m.nome, metodo_contanti = m.contanti, metodo_da_incassare = m.da_incassare,
    metodo_riscontro = m.riscontro, metodo_borsellino = m.borsellino
from metodo_pagamento m
where m.id = u.metodo_id;

-- Chi inserisce senza la copia (i dati demo, che girano dopo e non si possono cambiare) la riceve da qui, dal metodo
-- indicato. Il codice dell'applicazione scrive sempre la copia.
create function copia_metodo_pagamento() returns trigger language plpgsql as $$
begin
    if new.metodo_nome is null and new.metodo_id is not null then
        select m.nome, m.contanti, m.da_incassare, m.riscontro, m.borsellino
        into new.metodo_nome, new.metodo_contanti, new.metodo_da_incassare, new.metodo_riscontro, new.metodo_borsellino
        from metodo_pagamento m where m.id = new.metodo_id;
    end if;
    return new;
end $$;
create trigger pagamento_metodo_copia before insert on pagamento_metodo
    for each row execute function copia_metodo_pagamento();
create trigger uscita_metodo_copia before insert on uscita
    for each row execute function copia_metodo_pagamento();

alter table pagamento_metodo
    alter column metodo_nome set not null,
    alter column metodo_contanti set not null,
    alter column metodo_da_incassare set not null,
    alter column metodo_riscontro set not null,
    alter column metodo_borsellino set not null;
alter table uscita
    alter column metodo_nome set not null,
    alter column metodo_contanti set not null,
    alter column metodo_da_incassare set not null,
    alter column metodo_riscontro set not null,
    alter column metodo_borsellino set not null;

-- Il riferimento al metodo diventa facoltativo e si svuota se il metodo viene eliminato.
alter table pagamento_metodo alter column metodo_id drop not null;
alter table pagamento_metodo drop constraint pagamento_metodo_metodo_id_fkey;
alter table pagamento_metodo add constraint pagamento_metodo_metodo_id_fkey
    foreign key (metodo_id) references metodo_pagamento (id) on delete set null;

alter table uscita alter column metodo_id drop not null;
alter table uscita drop constraint uscita_metodo_id_fkey;
alter table uscita add constraint uscita_metodo_id_fkey
    foreign key (metodo_id) references metodo_pagamento (id) on delete set null;
