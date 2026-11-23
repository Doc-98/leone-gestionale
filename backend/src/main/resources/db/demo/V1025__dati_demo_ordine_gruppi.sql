-- Ordine dei gruppi di clienti (V35): le precedenze demo (V1023) arrivano dopo la V35, quindi si rifà l'ordine con la
-- stessa regola: prima i gruppi con precedenza, nel loro ordine, poi gli altri per nome.
update gruppo_clienti g set ordine = r.n
from (select id, row_number() over (order by precedenza nulls last, lower(nome)) as n from gruppo_clienti) r
where r.id = g.id;
