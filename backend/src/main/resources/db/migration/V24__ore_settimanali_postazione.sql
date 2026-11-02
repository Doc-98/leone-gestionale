-- Quante ore a settimana va coperta una postazione (ADR 0024): la pianificazione avvisa se ne pianifica meno e le
-- impostazioni stimano il costo del personale. Null = non indicato.
alter table postazione add column ore_settimanali numeric(5, 2) check (ore_settimanali > 0 and ore_settimanali <= 168);
