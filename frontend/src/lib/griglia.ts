import type { PersonaBreve, RigaModello, Turno } from './api';
import { daIso, GIORNI_BREVI, GIORNI_ESTESI, iso } from './date';

/** Una colonna della griglia: un giorno del calendario («2026-10-19») o della settimana tipo («1» = lunedì). */
export interface Colonna {
  chiave: string;
  etichetta: string;
  numero?: number;
  oggi?: boolean;
}

/** Un turno o una riga della settimana tipo, come lo disegna la griglia. */
export interface VoceGriglia {
  id: string;
  postazioneId: string;
  postazione: string;
  mansione: string;
  impiantoId: string;
  colonna: string;
  inizio: string;
  fine: string;
  persona: PersonaBreve | null;
  annullato: boolean;
  note: string | null;
}

export function colonneGiorni(giorni: string[], oggi = iso(new Date())): Colonna[] {
  return giorni.map((g) => {
    const d = daIso(g);
    return { chiave: g, etichetta: GIORNI_BREVI[((d.getDay() + 6) % 7) + 1], numero: d.getDate(), oggi: g === oggi };
  });
}

export const COLONNE_SETTIMANA: Colonna[] = [1, 2, 3, 4, 5, 6, 7].map((n) => ({ chiave: String(n), etichetta: GIORNI_ESTESI[n] }));

export function daTurno(t: Turno): VoceGriglia {
  return {
    id: t.id, postazioneId: t.postazioneId, postazione: t.postazione, mansione: t.mansione, impiantoId: t.impiantoId,
    colonna: t.data, inizio: t.inizio, fine: t.fine, persona: t.persona, annullato: t.stato === 'ANNULLATO', note: t.note,
  };
}

/** Le righe del modello non sanno la mansione: la prendono dalla postazione, se la conosciamo. */
export function daRiga(r: RigaModello, impiantoId: string, mansioni: Map<string, string>): VoceGriglia {
  return {
    id: r.id, postazioneId: r.postazioneId, postazione: r.postazione, mansione: mansioni.get(r.postazioneId) ?? 'Altro', impiantoId,
    colonna: String(r.giornoSettimana), inizio: r.inizio, fine: r.fine, persona: r.persona, annullato: false, note: null,
  };
}
