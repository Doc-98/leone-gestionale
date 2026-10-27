import { leggiPreferenza, salvaPreferenza } from './preferenze';

/**
 * I clienti aperti di recente, per la pagina Clienti: restano su questo dispositivo, per ogni account (come le scelte
 * di vista). Il più recente per primo, al massimo otto.
 */
export interface Recente {
  id: string;
  nome: string;
  cognome: string;
  eta: number | null;
  /** istante ISO dell'apertura */
  quando: string;
}

const NOME = 'clienti.recenti';
const MAX = 8;

export function recenti(): Recente[] {
  try {
    const v = JSON.parse(leggiPreferenza(NOME, '[]'));
    return Array.isArray(v) ? v.filter((x) => x && typeof x.id === 'string').slice(0, MAX) : [];
  } catch {
    return [];
  }
}

export function ricordaAperto(r: Omit<Recente, 'quando'>, adesso = new Date()) {
  const lista = [{ ...r, quando: adesso.toISOString() }, ...recenti().filter((x) => x.id !== r.id)].slice(0, MAX);
  salvaPreferenza(NOME, JSON.stringify(lista));
}
