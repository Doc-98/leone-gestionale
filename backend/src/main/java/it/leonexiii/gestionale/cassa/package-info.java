/**
 * Modulo cassa (ADR 0018, parte 11): le casse (una sola o una per postazione), le scelte su fondo e chiusura, le
 * aperture e chiusure con i contanti attesi, i contati e la differenza, i soldi tolti o messi a mano (versamenti,
 * piccole spese, aggiunte) e lo storico delle chiusure in PDF. I soldi entrati e usciti li legge da
 * {@link it.leonexiii.gestionale.pagamenti.Pagamenti#vociCassa}: ogni pagamento sa da quale cassa è passato.
 */
package it.leonexiii.gestionale.cassa;
