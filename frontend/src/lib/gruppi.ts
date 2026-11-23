/**
 * Le precedenze dei gruppi di clienti: l'ordine dell'elenco è anche l'ordine delle precedenze. La regola vive nel
 * backend; qui serve al riordino ottimistico, perché i tag «2ª precedenza» cambino subito, prima della risposta.
 */

/**
 * Copie nello stesso ordine, con `precedenza` = 1, 2, 3… tra i gruppi che ne hanno una (non null) e null per gli altri.
 * L'array di partenza non si tocca.
 */
export function rinumeraPrecedenze<T extends { id: string; precedenza: number | null }>(gruppi: T[]): T[] {
  let n = 0;
  return gruppi.map((g) => ({ ...g, precedenza: g.precedenza == null ? null : ++n }));
}
