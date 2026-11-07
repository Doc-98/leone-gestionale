-- Chi non paga (ADR 0018, parte 6): un controllo del tornello e un tipo di promemoria, entrambi spenti di base.

-- Il tornello avvisa dopo 7 giorni dalla scadenza e dà rosso dopo 30 (si cambiano nelle regole; senza i giorni del
-- rosso avvisa soltanto). Sotto la soglia in euro non succede niente.
insert into regola_accesso (codice, attiva, parametri) values
    ('PAGAMENTI', false, '{"giorniAvviso": 7, "giorniRosso": 30, "sogliaEuro": 0}');

-- Promemoria «pagamento scaduto»: il primo dopo N giorni dalla scadenza, poi ogni N giorni, al massimo M volte.
alter table promemoria drop constraint promemoria_tipo_check;
alter table promemoria add constraint promemoria_tipo_check
    check (tipo in ('CERTIFICATO', 'ABBONAMENTO', 'TESSERAMENTO', 'TESSERAMENTO_MANCANTE', 'PAGAMENTO'));

insert into promemoria (tipo, giorni, max_invii, attivo, oggetto, testo) values
('PAGAMENTO', 7, 3, false, 'Un pagamento da sistemare per {nome}',
'Buongiorno,

risulta ancora da pagare {importo} per {nome} {cognome}, scaduto il {scadenza}.

Si può pagare in segreteria negli orari di apertura. Se il pagamento è già stato fatto (per esempio con un bonifico),
può ignorare questo messaggio.

Grazie,
{societa}');
