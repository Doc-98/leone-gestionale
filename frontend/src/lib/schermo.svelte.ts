/** Disposizione dell'interfaccia: «desktop» sopra i 1024 px (menu laterale, griglie ampie), altrimenti «mobile». */
const QUERY = '(min-width: 1024px)';

class Schermo {
  larga = $state(typeof window !== 'undefined' && window.matchMedia?.(QUERY).matches === true);

  constructor() {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    window.matchMedia(QUERY).addEventListener('change', (e) => (this.larga = e.matches));
  }
}

export const schermo = new Schermo();
