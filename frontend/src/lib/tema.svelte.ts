/**
 * Il tema dell'interfaccia: chiaro, scuro o come il sistema. È una scelta di chi usa questo dispositivo (vale anche
 * prima di entrare, nella pagina di accesso), quindi resta nel browser e non nel server. Lo script in index.html la
 * applica prima del primo disegno; qui la si cambia e, con «Sistema», si segue il dispositivo quando cambia.
 */
export type SceltaTema = 'sistema' | 'chiaro' | 'scuro';

export const CHIAVE_TEMA = 'leone:tema';
const SCELTE: readonly SceltaTema[] = ['sistema', 'chiaro', 'scuro'];
const QUERY = '(prefers-color-scheme: dark)';

function leggi(): SceltaTema {
  try {
    const v = localStorage.getItem(CHIAVE_TEMA);
    if (v && (SCELTE as readonly string[]).includes(v)) return v as SceltaTema;
  } catch {
    /* niente memoria: come il sistema */
  }
  return 'sistema';
}

/** Il tema che si vede davvero, data la scelta e il tema del dispositivo. */
export function temaEffettivo(scelta: SceltaTema, sistemaScuro: boolean): 'chiaro' | 'scuro' {
  return scelta === 'sistema' ? (sistemaScuro ? 'scuro' : 'chiaro') : scelta;
}

class Tema {
  scelta = $state<SceltaTema>(leggi());
  sistemaScuro = $state(typeof window !== 'undefined' && window.matchMedia?.(QUERY).matches === true);
  readonly effettivo = $derived(temaEffettivo(this.scelta, this.sistemaScuro));

  constructor() {
    if (typeof window === 'undefined') return;
    window.matchMedia?.(QUERY).addEventListener('change', (e) => {
      this.sistemaScuro = e.matches;
      this.applica();
    });
    this.applica();
  }

  imposta(s: SceltaTema) {
    this.scelta = s;
    try {
      if (s === 'sistema') localStorage.removeItem(CHIAVE_TEMA);
      else localStorage.setItem(CHIAVE_TEMA, s);
    } catch {
      /* niente memoria: vale fino alla chiusura */
    }
    this.applica();
  }

  private applica() {
    if (typeof document !== 'undefined') document.documentElement.dataset.tema = temaEffettivo(this.scelta, this.sistemaScuro);
  }
}

export const tema = new Tema();
