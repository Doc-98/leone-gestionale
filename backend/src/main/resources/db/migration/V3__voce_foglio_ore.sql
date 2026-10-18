-- In quale colonna del foglio ore della società finiscono le ore di ogni mansione
-- (es. «ASSISTENTE SPOGLIATOIO»). Vuota: la mansione ha una colonna sua accanto a quelle del modello.
alter table mansione add column voce_foglio_ore varchar(80);
