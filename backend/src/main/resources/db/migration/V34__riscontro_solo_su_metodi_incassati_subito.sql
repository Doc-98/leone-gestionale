-- Il riscontro con lo scontrino del POS vale solo per i metodi incassati subito (non nel cassetto, non da incassare, non il
-- borsellino). Cambiando il tipo di un metodo, il riscontro si azzera da solo; qui si sistemano i metodi già salvati con
-- una combinazione impossibile (prima azzerava il riscontro solo il passaggio ai contanti, non quello a «da incassare»).
-- Le copie del metodo già conservate nei pagamenti e nelle uscite non cambiano: lo storico resta com'era.
update metodo_pagamento
   set riscontro = false
 where riscontro and (contanti or da_incassare or borsellino);
