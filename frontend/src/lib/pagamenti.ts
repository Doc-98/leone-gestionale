import type { Addebito, StatoAddebito } from './api';

/** Somma in centesimi, per non accumulare errori di arrotondamento. */
export function somma(valori: (number | string | null | undefined)[]): number {
  return valori.reduce<number>((t, v) => t + Math.round(Number(v || 0) * 100), 0) / 100;
}

/**
 * Come si divide un importo tra gli addebiti scelti, nell'ordine: il primo per intero, poi il successivo, finché
 * l'importo basta (come fa il server). Restituisce quanto va a ciascuno; quelli a zero restano da pagare.
 */
export function ripartisci(importo: number, addebiti: Pick<Addebito, 'id' | 'residuo'>[]): { id: string; quota: number }[] {
  let resto = Math.round(importo * 100);
  return addebiti.map((a) => {
    const q = Math.max(0, Math.min(resto, Math.round(a.residuo * 100)));
    resto -= q;
    return { id: a.id, quota: q / 100 };
  });
}

/** Prima quelli scaduti o che scadono prima. */
export function inOrdineDiScadenza<T extends Pick<Addebito, 'scadenza'>>(lista: T[]): T[] {
  return [...lista].sort((a, b) => a.scadenza.localeCompare(b.scadenza));
}

export const STATI_ADDEBITO: Record<StatoAddebito, { nome: string; classe: string }> = {
  DA_PAGARE: { nome: 'Da pagare', classe: 't-altro' },
  SCADUTO: { nome: 'Scaduto', classe: 't-warn' },
  PAGATO: { nome: 'Pagato', classe: 't-soft' },
  ANNULLATO: { nome: 'Annullato', classe: 't-altro' },
  CREDITO: { nome: 'Credito', classe: 't-soft' },
};

export const aperto = (a: Pick<Addebito, 'stato'>) => a.stato === 'DA_PAGARE' || a.stato === 'SCADUTO';
