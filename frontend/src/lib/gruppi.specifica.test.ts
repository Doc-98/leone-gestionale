import { describe, expect, it } from 'vitest';
import { rinumeraPrecedenze } from './gruppi';

/**
 * Specifica (brief coerenza 7, lotto 3, A1 «lib/gruppi.ts»): `rinumeraPrecedenze(gruppi)` restituisce copie nello
 * stesso ordine, con `precedenza` = 1, 2, 3… tra quelli che l'hanno non null, null per gli altri. Serve al riordino
 * ottimistico: i tag «Nª precedenza» cambiano subito. La precedenza di un gruppo è la sua posizione tra i gruppi con la
 * precedenza accesa, nell'ordine dell'elenco.
 */
interface G { id: string; nome: string; precedenza: number | null }
const g = (id: string, precedenza: number | null): G => ({ id, nome: `Gruppo ${id}`, precedenza });
const prec = (r: G[]) => r.map((x) => x.precedenza);
const ids = (r: G[]) => r.map((x) => x.id);

describe('rinumeraPrecedenze', () => {
  it('numeri consecutivi nell\'ordine dato, anche se quelli di partenza non lo erano', () => {
    const r = rinumeraPrecedenze([g('a', 3), g('b', 1), g('c', 2)]);
    expect(prec(r)).toEqual([1, 2, 3]);
    expect(ids(r)).toEqual(['a', 'b', 'c']);
  });

  it('i gruppi senza precedenza restano senza e non occupano un numero', () => {
    const r = rinumeraPrecedenze([g('a', null), g('b', 7), g('c', null), g('d', 2), g('e', null), g('f', 9)]);
    expect(prec(r)).toEqual([null, 1, null, 2, null, 3]);
  });

  it('dopo un riordino la precedenza segue la posizione (b passa davanti ad a)', () => {
    const riordinati = [g('b', 2), g('a', 1), g('c', null)];
    expect(prec(rinumeraPrecedenze(riordinati))).toEqual([1, 2, null]);
  });

  it('tutti senza precedenza: restano tutti null', () => {
    expect(prec(rinumeraPrecedenze([g('a', null), g('b', null)]))).toEqual([null, null]);
  });

  it('elenco vuoto → elenco vuoto', () => {
    expect(rinumeraPrecedenze([])).toEqual([]);
  });

  it('un solo gruppo con precedenza → 1', () => {
    expect(prec(rinumeraPrecedenze([g('a', 5)]))).toEqual([1]);
  });

  it('gli altri campi restano intatti (anche quelli che la funzione non conosce)', () => {
    const origine = [{ ...g('a', 4), descrizione: 'Agonisti', attivo: false }, { ...g('b', null), attivo: true }];
    const r = rinumeraPrecedenze(origine);
    expect(r[0]).toEqual({ id: 'a', nome: 'Gruppo a', precedenza: 1, descrizione: 'Agonisti', attivo: false });
    expect(r[1]).toEqual({ id: 'b', nome: 'Gruppo b', precedenza: null, attivo: true });
  });

  it('non modifica l\'array né gli oggetti di partenza (restituisce copie)', () => {
    const a = g('a', 3);
    const b = g('b', null);
    const c = g('c', 1);
    const origine = Object.freeze([Object.freeze(a), Object.freeze(b), Object.freeze(c)]) as unknown as G[];
    const r = rinumeraPrecedenze(origine);
    expect(r).not.toBe(origine);
    expect(prec(r)).toEqual([1, null, 2]);
    expect(prec([a, b, c])).toEqual([3, null, 1]);
    expect(r[0]).not.toBe(a);
  });

  it('è idempotente', () => {
    const una = rinumeraPrecedenze([g('a', 9), g('b', null), g('c', 4)]);
    expect(rinumeraPrecedenze(una)).toEqual(una);
  });
});
