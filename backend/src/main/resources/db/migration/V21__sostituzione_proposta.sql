-- Sostituzioni (ADR 0022): chi la chiede ha avvisato che sarà assente, quindi non si respinge. Il coordinatore può
-- proporla a un collega preciso (proposta_da = chi l'ha proposta): se il collega accetta, è già approvata; se rifiuta,
-- torna aperta a tutti. Se nessuno la prende, il coordinatore lascia il turno scoperto.
alter table richiesta_turno add column proposta_da uuid references persona (id);
