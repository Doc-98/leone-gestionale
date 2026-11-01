import { daIso, GIORNI_BREVI, iso } from './date';

/** Una colonna della griglia: un giorno del calendario («2026-10-19») o della settimana tipo («1» = lunedì). */
export interface Colonna {
  chiave: string;
  etichetta: string;
  numero?: number;
  oggi?: boolean;
}

export function colonneGiorni(giorni: string[], oggi = iso(new Date())): Colonna[] {
  return giorni.map((g) => {
    const d = daIso(g);
    return { chiave: g, etichetta: GIORNI_BREVI[((d.getDay() + 6) % 7) + 1], numero: d.getDate(), oggi: g === oggi };
  });
}
