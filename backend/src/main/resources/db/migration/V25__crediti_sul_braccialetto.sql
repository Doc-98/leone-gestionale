-- Crediti di phon e docce scritti sul braccialetto (ADR 0025). Con macchine non collegate alla rete il saldo vero è
-- nella memoria del braccialetto: il gestionale lo scrive alla ricarica e ne conosce solo l'ultimo valore visto.

-- dove sta il credito: SERVER (le macchine chiedono al gestionale) o BRACCIALETTO (lo leggono e scrivono sul chip)
alter table regole_borsellino add column crediti_su varchar(12) not null default 'SERVER'
    check (crediti_su in ('SERVER', 'BRACCIALETTO'));

-- l'ultimo saldo visto su ogni braccialetto: scritto alla ricarica, letto al banco o scritto a mano dal display
create table saldo_braccialetto (
    braccialetto_id uuid primary key references braccialetto (id),
    crediti         int not null check (crediti >= 0),
    fonte           varchar(12) not null check (fonte in ('LETTURA', 'SCRITTURA', 'A_MANO')),
    aggiornato_il   timestamptz not null,
    aggiornato_da   uuid references persona (id)
);

-- una ricarica scritta sul braccialetto, in due tempi: preparata (il server ha calcolato la memoria nuova), poi
-- confermata (il PC l'ha scritta e riletta: solo allora si registra il pagamento) o annullata
create table scrittura_braccialetto (
    id              uuid primary key,
    braccialetto_id uuid not null references braccialetto (id),
    persona_id      uuid not null references persona (id),
    crediti_prima   int not null check (crediti_prima >= 0),
    crediti_dopo    int not null check (crediti_dopo >= 0),
    importo         numeric(10, 2) not null check (importo > 0),
    stato           varchar(12) not null check (stato in ('PREPARATA', 'CONFERMATA', 'ANNULLATA')),
    pagamento_id    uuid,
    creata_il       timestamptz not null,
    creata_da       uuid references persona (id),
    chiusa_il       timestamptz
);
create index scrittura_braccialetto_aperte on scrittura_braccialetto (braccialetto_id) where stato = 'PREPARATA';
