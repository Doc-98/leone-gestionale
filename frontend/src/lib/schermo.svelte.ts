/** Disposizione dell'interfaccia: «desktop» sopra i 1024 px (menu laterale, griglie ampie), altrimenti «mobile». */
const QUERY = '(min-width: 1024px)';
/** Telefono: sotto i 640 px (il tablet sta in mezzo). */
const QUERY_TELEFONO = '(max-width: 639px)';

class Schermo {
  larga = $state(typeof window !== 'undefined' && window.matchMedia?.(QUERY).matches === true);
  telefono = $state(typeof window !== 'undefined' && window.matchMedia?.(QUERY_TELEFONO).matches === true);

  constructor() {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    window.matchMedia(QUERY).addEventListener('change', (e) => (this.larga = e.matches));
    window.matchMedia(QUERY_TELEFONO).addEventListener('change', (e) => (this.telefono = e.matches));
  }
}

export const schermo = new Schermo();
