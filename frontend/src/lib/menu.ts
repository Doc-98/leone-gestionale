/**
 * Logica del menu «⋯» (design system, «Componenti comuni»): l'ordine delle voci e il tasto che sposta il focus.
 * Funzioni pure: le usa `components/MenuAltro.svelte`.
 */

import type { NomeIcona } from './icone';

/** Una voce del menu «⋯». */
export interface VoceMenu {
  /** Verbo + oggetto: «Modifica l'anagrafica», «Nuovo certificato…» (i puntini dicono che si apre un modulo). */
  testo: string;
  icona: NomeIcona;
  onclick: () => unknown;
  /** La voce che elimina: rossa, ultima, con i due tocchi dentro la voce. Il testo della domanda: «Elimina?», «Rimuovi?». */
  elimina?: 'Elimina?' | 'Rimuovi?';
  /** Rossa e ultima ma senza i due tocchi: apre un modulo che fa da conferma («Banna la persona…»). */
  rossa?: boolean;
  disabled?: boolean;
  /** Una linea di separazione sopra la voce (tra gruppi di voci). */
  separatore?: boolean;
}

/** Il massimo di voci di un menu «⋯»: oltre, la pagina sta nascondendo troppe cose nel menu. */
export const MAX_VOCI_MENU = 7;

/**
 * Le voci nell'ordine da mostrare: in fondo quelle che chiudono il menu (prima la voce rossa che apre un modulo, `rossa`,
 * poi quella che elimina, `elimina`); le altre restano nell'ordine dato. Lancia un errore se le voci sono più di 7 o se
 * ce n'è più di una che elimina: è un errore di chi scrive la pagina, da vedere subito.
 */
export function ordinaVoci<T extends { elimina?: string; rossa?: boolean }>(voci: T[]): T[] {
  if (voci.length > MAX_VOCI_MENU) throw new Error(`Un menu «⋯» ha al massimo ${MAX_VOCI_MENU} voci (ce ne sono ${voci.length}).`);
  if (voci.filter((v) => v.elimina).length > 1) throw new Error('Un menu «⋯» ha al massimo una voce che elimina.');
  const peso = (v: T) => (v.elimina ? 2 : v.rossa ? 1 : 0);
  // `sort` è stabile: a parità di peso resta l'ordine dato
  return [...voci].sort((a, b) => peso(a) - peso(b));
}

/**
 * La voce che prende il focus dopo un tasto: ArrowDown e ArrowUp a giro, Home la prima, End l'ultima; -1 per gli altri tasti.
 * `i` è la voce che ha il focus adesso (-1 se nessuna). Salta gli indici in `spente`; -1 anche se non ce n'è nessuna accesa.
 */
export function voceDopoTasto(tasto: string, i: number, n: number, spente: ReadonlySet<number> = new Set()): number {
  if (n <= 0) return -1;
  const accesa = (k: number) => !spente.has(k);
  const cerca = (da: number, passo: 1 | -1): number => {
    for (let k = 1; k <= n; k++) {
      const j = (((da + passo * k) % n) + n) % n;
      if (accesa(j)) return j;
    }
    return -1;
  };
  switch (tasto) {
    case 'ArrowDown': return cerca(i < 0 ? -1 : i, 1);
    case 'ArrowUp': return cerca(i < 0 ? 0 : i, -1);
    case 'Home': return cerca(-1, 1);
    case 'End': return cerca(0, -1);
    default: return -1;
  }
}
