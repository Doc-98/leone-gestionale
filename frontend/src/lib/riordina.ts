/**
 * Riordino degli elenchi modificabili (ADR 0028): funzioni pure sugli id. Non toccano mai l'array ricevuto.
 */

/** Il nuovo ordine dopo aver tolto l'elemento in `da` e averlo rimesso in `a` (trascinamento). Indici fuori elenco: nessun cambio. */
export function sposta(ids: string[], da: number, a: number): string[] {
  const copia = [...ids];
  const dentro = (i: number) => Number.isInteger(i) && i >= 0 && i < ids.length;
  if (!dentro(da) || !dentro(a) || da === a) return copia;
  const [elemento] = copia.splice(da, 1);
  copia.splice(a, 0, elemento);
  return copia;
}

/** Sposta di un posto (frecce su/giù sulla maniglia). Ai bordi, o con un indice fuori elenco, non cambia nulla. */
export function spostaDiUno(ids: string[], indice: number, verso: -1 | 1): string[] {
  return sposta(ids, indice, indice + verso);
}

/** «Satispay spostato al posto 2 di 5»: l'annuncio per lo screen reader dopo uno spostamento. */
export function annuncioSpostamento(nome: string, posto: number, totale: number): string {
  return `${nome} spostato al posto ${posto} di ${totale}`;
}

/**
 * Durante il trascinamento: dato il punto del puntatore e i bordi verticali delle righe (misurati all'inizio del gesto),
 * dice in quale posizione finirebbe l'elemento. Sopra la prima riga è 0, sotto l'ultima è l'ultima.
 */
export function postoDalPunto(y: number, righe: { top: number; bottom: number }[]): number {
  if (righe.length === 0) return 0;
  for (let i = 0; i < righe.length; i++) if (y < righe[i].bottom) return i;
  return righe.length - 1;
}

/** Stesso ordine? (per sapere se un trascinamento ha cambiato qualcosa) */
export function stessoOrdine(a: string[], b: string[]): boolean {
  return a.length === b.length && a.every((x, i) => x === b[i]);
}
