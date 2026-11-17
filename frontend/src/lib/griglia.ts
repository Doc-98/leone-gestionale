import { iso } from './date';
import { giornoBreve } from './turni';

/** Una colonna della griglia: un giorno del calendario («2026-10-19») o della settimana tipo («1» = lunedì). */
export interface Colonna {
  chiave: string;
  etichetta: string;
  numero?: number;
  oggi?: boolean;
}

export function colonneGiorni(giorni: string[], oggi = iso(new Date())): Colonna[] {
  return giorni.map((g) => {
    const { l, n } = giornoBreve(g);
    return { chiave: g, etichetta: l, numero: n, oggi: g === oggi };
  });
}
