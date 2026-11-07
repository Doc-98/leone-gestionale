/**
 * Modulo ricevute (ADR 0018, parte 10): le serie numerate (ricevute, fatture), le scelte su cosa c'è scritto e come si
 * consegnano, e le ricevute fatte dai pagamenti, una per famiglia o una per persona, in PDF da stampare o mandare per
 * email. Un numero non torna mai libero: una ricevuta annullata resta, con il motivo. Usa
 * {@link it.leonexiii.gestionale.pagamenti.Pagamenti} e ascolta
 * {@link it.leonexiii.gestionale.pagamenti.Pagamenti.PagamentoAnnullato} per annullare le ricevute di un pagamento.
 */
package it.leonexiii.gestionale.ricevute;
