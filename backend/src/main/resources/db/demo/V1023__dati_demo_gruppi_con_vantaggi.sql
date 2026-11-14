-- Gruppi con vantaggi (ADR 0026): gli alunni dell'Istituto hanno la precedenza più forte e 7 giorni per pagare, e le
-- famiglie possono dichiararlo; la squadra agonistica viene dopo. Una dichiarazione di Emma Fiore aspetta la verifica.
update gruppo_clienti set precedenza = 1, giorni_per_pagare = 7, dichiarabile = true,
    frase_dichiarazione = 'Frequenta la scuola dell''Istituto'
where id = '00000000-0000-0000-0000-000000000901';
update gruppo_clienti set precedenza = 2 where id = '00000000-0000-0000-0000-000000000902';

insert into appartenenza_gruppo (gruppo_id, persona_id, dal, verificata) values
    ('00000000-0000-0000-0000-000000000901', '00000000-0000-0000-0000-000000000509', current_date - 3, false);
