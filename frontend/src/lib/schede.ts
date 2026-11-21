/**
 * Logica delle schede con frecce (design system, «Schede lunghe»): quale scheda dopo un tasto, quando mostrare le frecce
 * di scorrimento, dove scorrere per tenere in vista la scheda attiva. Funzioni pure: le usa `components/Schede.svelte`.
 */

/** Una scheda: `segno` è il segno accanto al nome (un conteggio, o ciò che manca: ambra con `warn`). */
export interface Scheda<S extends string = string> { id: S; nome: string; segno?: { testo: string; warn?: boolean } }

/** La scheda che si apre dopo un tasto: frecce sinistra e destra a giro, Inizio e Fine; -1 per gli altri tasti. */
export function schedaDopoTasto(tasto: string, i: number, n: number): number {
  if (n <= 0) return -1;
  switch (tasto) {
    case 'ArrowRight': return (i + 1) % n;
    case 'ArrowLeft': return (i - 1 + n) % n;
    case 'Home': return 0;
    case 'End': return n - 1;
    default: return -1;
  }
}

/** Da che parte c'è ancora qualcosa da scorrere. Tolleranza di 1 px: lo scorrimento ha decimali sui monitor ad alta densità. */
export function frecceVisibili(scrollLeft: number, larghezza: number, totale: number): { sx: boolean; dx: boolean } {
  return { sx: scrollLeft > 1, dx: totale - (scrollLeft + larghezza) > 1 };
}

/**
 * Lo scrollLeft per avere in vista la scheda che va da `inizio` a `inizio + larghezza`, con `margine` libero da ogni lato
 * (le frecce stanno sopra i bordi); invariato se è già in vista. Se la scheda è più larga della vista vince il suo inizio.
 */
export function scorriPerVedere(inizio: number, larghezza: number, scrollLeft: number, vista: number, margine = 0): number {
  const da = inizio - margine;
  const a = inizio + larghezza + margine;
  if (da < scrollLeft || a - da > vista) return Math.max(0, da);
  if (a > scrollLeft + vista) return Math.max(0, a - vista);
  return scrollLeft;
}
