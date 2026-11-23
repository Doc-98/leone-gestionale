import type { Stagione } from './api';

/**
 * Lo stato di una stagione rispetto a oggi (design system, «Scelta della stagione»): in corso, finita o futura.
 * Funzioni pure: la data di oggi si passa da fuori (`iso(new Date())`), così si provano senza orologio.
 */
export type StatoStagione = 'in corso' | 'finita' | 'futura';

/** Gli estremi contano: `dal <= oggi <= al` è in corso. Le date sono «AAAA-MM-GG», si confrontano come testo. */
export function statoStagione(s: { dal: string; al: string }, oggi: string): StatoStagione {
  if (oggi < s.dal) return 'futura';
  if (oggi > s.al) return 'finita';
  return 'in corso';
}

/** Le classi della pillola: «in corso» verde, «finita» grigia, «futura» azzurra (`.pill.futura`, token `--futura-*`). */
export function classePillStagione(stato: StatoStagione): string {
  return stato === 'in corso' ? 'pill passa' : stato === 'futura' ? 'pill futura' : 'pill';
}

/** Una copia in ordine di inizio, la più vecchia per prima. */
export function ordinaStagioni<T extends { dal: string }>(stagioni: T[]): T[] {
  return [...stagioni].sort((a, b) => a.dal.localeCompare(b.dal));
}

/** La stagione da mostrare all'apertura: quella in corso; se non c'è la futura più vicina; se non c'è l'ultima finita; `''` senza stagioni. */
export function stagioneIniziale(stagioni: Pick<Stagione, 'id' | 'dal' | 'al'>[], oggi: string): string {
  const ordine = ordinaStagioni(stagioni);
  const scelta =
    ordine.find((s) => statoStagione(s, oggi) === 'in corso') ??
    ordine.find((s) => statoStagione(s, oggi) === 'futura') ??
    ordine.at(-1);
  return scelta?.id ?? '';
}
