-- Gruppi di clienti con vantaggi (ADR 0026): oltre ai prezzi, un gruppo può dare
-- precedenza: posizione nell'ordine dei gruppi con precedenza (1 = la più forte); vuota = nessuna precedenza.
-- giorni_per_pagare: dopo il posto offerto; vuoto = i giorni di base della campagna.
-- dichiarabile: le famiglie lo possono segnare nelle richieste (es. «Frequenta la scuola Leone XIII»), con questa frase;
--               la segreteria lo verifica e finché non è verificato non dà vantaggi.
alter table gruppo_clienti
    add column precedenza          int check (precedenza >= 1),
    add column giorni_per_pagare   int check (giorni_per_pagare between 1 and 60),
    add column dichiarabile        boolean not null default false,
    add column frase_dichiarazione varchar(160),
    add check (not dichiarabile or frase_dichiarazione is not null);

-- Un'appartenenza dichiarata dalla famiglia nasce da verificare; quelle messe dalla segreteria sono già verificate.
alter table appartenenza_gruppo
    add column verificata    boolean not null default true,
    add column verificata_il timestamptz,
    add column verificata_da uuid references persona (id);
create index appartenenza_gruppo_da_verificare on appartenenza_gruppo (gruppo_id) where not verificata and al is null;
