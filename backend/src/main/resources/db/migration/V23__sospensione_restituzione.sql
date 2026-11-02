-- La regola di sospensione si applica al banco (ADR 0024): ogni sospensione ricorda quanti giorni allunga
-- l'abbonamento e quanto credito ha messo nel borsellino. Le sospensioni di prima (giorni_allungati null)
-- allungano l'abbonamento di tutti i giorni sospesi, come prima.
alter table sospensione add column giorni_allungati integer check (giorni_allungati >= 0);
alter table sospensione add column credito numeric(10, 2) not null default 0 check (credito >= 0);
