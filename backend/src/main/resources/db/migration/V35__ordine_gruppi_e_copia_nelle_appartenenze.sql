-- Gruppi di clienti: l'ordine dell'elenco (la maniglia) è anche l'ordine delle precedenze nelle graduatorie, e un gruppo
-- eliminato lascia nello storico delle appartenenze la copia del suo nome (ADR 0029).

-- Ordine dell'elenco: prima i gruppi con precedenza (nel loro ordine), poi gli altri per nome.
alter table gruppo_clienti add column ordine integer not null default 0;
update gruppo_clienti g set ordine = r.n
from (select id, row_number() over (order by precedenza nulls last, lower(nome)) as n from gruppo_clienti) r
where r.id = g.id;

-- Le appartenenze chiuse di un gruppo eliminato tengono il nome di allora; senza gruppo sono sempre chiuse.
alter table appartenenza_gruppo add column gruppo_nome varchar(80);
alter table appartenenza_gruppo alter column gruppo_id drop not null;
alter table appartenenza_gruppo add constraint appartenenza_gruppo_senza_gruppo_chiusa
    check (gruppo_id is not null or (gruppo_nome is not null and al is not null));
