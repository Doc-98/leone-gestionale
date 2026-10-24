-- Pagamenti per una società qualsiasi (ADR 0018, parte 5):
-- · permesso «Acconti e pagamenti misti»: chi non ce l'ha salda per intero quello che sceglie, con un solo metodo.
--   Di partenza a coordinatori e segreteria (si toglie dai ruoli).
-- · come si registrano i pagamenti di più familiari insieme: un pagamento unico per la famiglia (INSIEME), uno per
--   ogni persona (PER_PERSONA), oppure lo si sceglie ogni volta (A_SCELTA, di partenza).
insert into ruolo_permesso (ruolo, permesso) values ('COORDINATORE', 'PAGAMENTI_FLESSIBILI'), ('SEGRETERIA', 'PAGAMENTI_FLESSIBILI');

alter table societa add column pagamenti_famiglia varchar(12) not null default 'A_SCELTA'
    check (pagamenti_famiglia in ('INSIEME', 'PER_PERSONA', 'A_SCELTA'));
