/**
 * Segmentati con le voci tutte della stessa larghezza (quella della più lunga) che, se non ci stanno in fila, si
 * impilano una sotto l'altra (design system, «Scelta a tre nel foglio»): mai testo che sfora o va a capo, mai overflow
 * della pagina.
 */

/** Quanto è largo il segmentato in fila e se ci sta: ogni voce larga quanto la più lunga, più gli spazi tra le voci e il bordo. */
export function misuraSegmentato(voci: number[], spazio: number, bordo: number, disponibile: number): { colonna: number; naturale: number; impila: boolean } {
  const colonna = Math.ceil(Math.max(0, ...voci));
  const naturale = voci.length * colonna + Math.max(0, voci.length - 1) * spazio + bordo;
  // senza una misura (riga non ancora disegnata) non si impila; mezzo pixel di tolleranza per gli arrotondamenti
  return { colonna, naturale, impila: disponibile > 0 && naturale > disponibile + 0.5 };
}

/**
 * Azione Svelte per un `.seg`: misura le voci (ognuna larga quanto il suo testo), dà a tutte la larghezza della più
 * lunga (`--col`) e mette `data-impilato` se in fila non ci stanno nella riga che le contiene (`.riga-imp` o `.field`,
 * senza il padding). Si rimisura a ogni cambio di larghezza della riga e quando i caratteri sono caricati.
 */
export function impila(nodo: HTMLElement) {
  const riga = (nodo.closest('.riga-imp, .field') as HTMLElement | null) ?? nodo.parentElement;
  if (!riga) return;
  const misura = () => {
    nodo.removeAttribute('data-impilato');
    nodo.style.gridTemplateColumns = 'repeat(var(--n), max-content)'; // ogni voce larga quanto il suo testo
    nodo.style.width = 'max-content';
    const voci = [...nodo.children].map((b) => b.getBoundingClientRect().width);
    nodo.style.gridTemplateColumns = '';
    nodo.style.width = '';
    const s = getComputedStyle(nodo);
    const bordo = parseFloat(s.paddingLeft || '0') + parseFloat(s.paddingRight || '0');
    const r = getComputedStyle(riga);
    const disponibile = riga.clientWidth - parseFloat(r.paddingLeft || '0') - parseFloat(r.paddingRight || '0');
    const m = misuraSegmentato(voci, parseFloat(s.columnGap || '0'), bordo, disponibile);
    if (m.colonna > 0) nodo.style.setProperty('--col', `${m.colonna}px`);
    if (m.impila) nodo.setAttribute('data-impilato', '');
  };
  misura();
  const osservatore = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(misura);
  osservatore?.observe(riga);
  document.fonts?.ready.then(misura).catch(() => {});
  return { destroy: () => osservatore?.disconnect() };
}
