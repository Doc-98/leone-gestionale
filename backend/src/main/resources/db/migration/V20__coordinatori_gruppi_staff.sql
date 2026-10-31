-- Gruppi di staff (ADR 0022): il gruppo è la mansione (assistenti spogliatoio, atrio, bagnanti…). Chi è indicato come
-- coordinatore di uno o più gruppi, con il permesso «Ore dello staff», vede le ore solo di quei gruppi; chi ha il
-- permesso senza essere coordinatore di nessun gruppo dell'impianto (la direzione) li vede tutti.
create table mansione_coordinatore (
    mansione_id uuid not null references mansione (id) on delete cascade,
    persona_id  uuid not null references persona (id),
    primary key (mansione_id, persona_id)
);
create index mansione_coordinatore_persona on mansione_coordinatore (persona_id);
