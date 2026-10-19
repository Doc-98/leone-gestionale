-- Le corsie di una vasca cambiano con i divisori: la vasca grande si usa a 6 oppure a 5 corsie.
-- Ogni corsia appartiene a un «assetto» (numero di corsie in cui è divisa la vasca). Due assetti diversi della
-- stessa vasca non possono essere usati nello stesso momento.
alter table spazio add column assetto smallint check (assetto between 1 and 12);

-- Colore del corso (es. i livelli Delfino Giallo, Verde, Azzurro…), facoltativo, in esadecimale «#RRGGBB»
alter table corso add column colore varchar(7) check (colore ~ '^#[0-9A-Fa-f]{6}$');
