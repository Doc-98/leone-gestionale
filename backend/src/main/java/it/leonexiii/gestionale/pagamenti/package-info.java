/**
 * Modulo pagamenti (ADR 0018, parte 5): gli addebiti (cosa una persona deve, con la scadenza, anche a rate), i
 * pagamenti (chi paga, anche un genitore per più figli, con più metodi insieme) e i metodi di pagamento modificabili.
 * Il bonifico si registra «da incassare» e si segna incassato a mano. Gli altri moduli usano
 * {@link it.leonexiii.gestionale.pagamenti.Pagamenti}; quando un tesseramento risulta pagato, o non più pagato, il
 * modulo pubblica {@link it.leonexiii.gestionale.pagamenti.Pagamenti.Saldato}.
 */
package it.leonexiii.gestionale.pagamenti;
