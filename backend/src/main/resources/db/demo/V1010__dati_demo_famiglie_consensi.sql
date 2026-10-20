-- Scheda completa (dati inventati): anagrafica con codice fiscale, tutori e fratelli, consensi privacy.
-- I codici fiscali sono costruiti con l'algoritmo ma su persone inventate.

-- ── Anagrafica dei casi fissi ───────────────────────────────────────────────
update persona set sesso = 'M', luogo_nascita = 'Lanciano', codice_fiscale = 'NRETMS17H03E435F', indirizzo = 'Via delle Rose 12',
    cap = '66034', citta = 'Lanciano', provincia = 'CH', nazione = 'Italia'
where id = '00000000-0000-0000-0000-000000000502';
update persona set sesso = 'F', luogo_nascita = 'Pescara', codice_fiscale = 'RGGGRG16B61G482L', indirizzo = 'Viale dei Tigli 4',
    cap = '65121', citta = 'Pescara', provincia = 'PE', nazione = 'Italia'
where id = '00000000-0000-0000-0000-000000000503';
update persona set sesso = 'F', luogo_nascita = 'Pescara', citta = 'Pescara', provincia = 'PE', nazione = 'Italia'
where id = '00000000-0000-0000-0000-000000000512';

-- ── Tutori e una sorella ────────────────────────────────────────────────────
insert into persona (id, nome, cognome, data_nascita, sesso, luogo_nascita, codice_fiscale, email, telefono, indirizzo, cap, citta, provincia, nazione) values
    ('00000000-0000-0000-0000-000000000521', 'Paola', 'Neri', '1982-04-05', 'F', 'Lanciano', 'NREPLA82D45E435J', 'paola.neri@example.org',
     '333 0000521', 'Via delle Rose 12', '66034', 'Lanciano', 'CH', 'Italia'),
    ('00000000-0000-0000-0000-000000000522', 'Sara', 'Neri', '2019-09-12', 'F', 'Lanciano', 'NRESRA19P52E435T', null,
     null, 'Via delle Rose 12', '66034', 'Lanciano', 'CH', 'Italia'),
    ('00000000-0000-0000-0000-000000000523', 'Marco', 'Ruggiero', '1980-12-10', 'M', 'Pescara', 'RGGMRC80T10G482K', 'marco.ruggiero@example.org',
     '333 0000523', 'Viale dei Tigli 4', '65121', 'Pescara', 'PE', 'Italia'),
    ('00000000-0000-0000-0000-000000000524', 'Laura', 'Russo', '1984-03-10', 'F', 'Pescara', 'RSSLRA84C50G482P', 'laura.russo@example.org',
     '333 0000524', null, null, 'Pescara', 'PE', 'Italia');

insert into legame (persona_id, tutore_id, relazione, comunicazioni, delegato_ritiro, dal) values
    ('00000000-0000-0000-0000-000000000502', '00000000-0000-0000-0000-000000000521', 'MADRE', true, true, current_date - 40),
    ('00000000-0000-0000-0000-000000000522', '00000000-0000-0000-0000-000000000521', 'MADRE', true, true, current_date - 40),
    ('00000000-0000-0000-0000-000000000503', '00000000-0000-0000-0000-000000000523', 'PADRE', true, true, current_date - 300),
    ('00000000-0000-0000-0000-000000000512', '00000000-0000-0000-0000-000000000524', 'MADRE', true, true, current_date - 4);

-- ── Consensi ────────────────────────────────────────────────────────────────
insert into consenso (persona_id, tipo, firmato_da, dato_il, valido_fino, email, sms, cartaceo) values
    ('00000000-0000-0000-0000-000000000502', 'TRATTAMENTO', 'Paola Neri (madre)', current_date - 40, null, false, false, false),
    ('00000000-0000-0000-0000-000000000502', 'IMMAGINI', 'Paola Neri (madre)', current_date - 40, current_date + 20, false, false, false),
    ('00000000-0000-0000-0000-000000000522', 'TRATTAMENTO', 'Paola Neri (madre)', current_date - 40, null, false, false, false),
    ('00000000-0000-0000-0000-000000000503', 'TRATTAMENTO', 'Marco Ruggiero (padre)', current_date - 300, null, false, false, false),
    ('00000000-0000-0000-0000-000000000503', 'MARKETING', 'Marco Ruggiero (padre)', current_date - 300, null, true, false, false),
    ('00000000-0000-0000-0000-000000000512', 'TRATTAMENTO', 'Laura Russo (madre)', current_date - 4, null, false, false, false),
    ('00000000-0000-0000-0000-000000000501', 'TRATTAMENTO', 'Sofia Marchetti', current_date - 200, null, false, false, false);
