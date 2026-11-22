import { describe, expect, it } from 'vitest';
import { sposta, spostaDiUno } from './riordina';

/**
 * Specifica (brief lotto 4, «Logica in riordina.ts»): funzioni pure.
 * - sposta(ids, da, a): il nuovo ordine dopo aver tolto l'elemento in posizione `da` e averlo rimesso in posizione `a`
 *   (trascinamento); `a` è la posizione finale dell'elemento.
 * - spostaDiUno(ids, indice, verso): freccia su (-1) / giù (+1); ai bordi non cambia nulla.
 * Né l'una né l'altra modifica l'array in ingresso.
 */
const ABCDE = ['a', 'b', 'c', 'd', 'e'];

describe('sposta – trascinamento', () => {
  it('porta un elemento in avanti: l\'elemento finisce alla posizione `a`', () => {
    const r = sposta(ABCDE, 1, 3);
    expect(r).toEqual(['a', 'c', 'd', 'b', 'e']);
    expect(r[3]).toBe('b');
  });

  it('porta un elemento indietro', () => {
    const r = sposta(ABCDE, 3, 1);
    expect(r).toEqual(['a', 'd', 'b', 'c', 'e']);
    expect(r[1]).toBe('d');
  });

  it('dal primo all\'ultimo posto', () => {
    expect(sposta(ABCDE, 0, 4)).toEqual(['b', 'c', 'd', 'e', 'a']);
  });

  it('dall\'ultimo al primo posto', () => {
    expect(sposta(ABCDE, 4, 0)).toEqual(['e', 'a', 'b', 'c', 'd']);
  });

  it('di un solo posto equivale allo scambio dei due vicini', () => {
    expect(sposta(ABCDE, 2, 3)).toEqual(['a', 'b', 'd', 'c', 'e']);
    expect(sposta(ABCDE, 2, 1)).toEqual(['a', 'c', 'b', 'd', 'e']);
  });

  it('da === a: stesso ordine', () => {
    expect(sposta(ABCDE, 2, 2)).toEqual(ABCDE);
    expect(sposta(ABCDE, 0, 0)).toEqual(ABCDE);
    expect(sposta(ABCDE, 4, 4)).toEqual(ABCDE);
  });

  it('conserva gli stessi elementi, senza perderne né duplicarne', () => {
    for (let da = 0; da < ABCDE.length; da++) {
      for (let a = 0; a < ABCDE.length; a++) {
        const r = sposta(ABCDE, da, a);
        expect(r).toHaveLength(ABCDE.length);
        expect([...r].sort()).toEqual([...ABCDE].sort());
        expect(r[a]).toBe(ABCDE[da]);
      }
    }
  });

  it('lista di un solo elemento e lista vuota', () => {
    expect(sposta(['x'], 0, 0)).toEqual(['x']);
    expect(sposta([], 0, 0)).toEqual([]);
  });

  it('due elementi: si scambiano', () => {
    expect(sposta(['x', 'y'], 0, 1)).toEqual(['y', 'x']);
    expect(sposta(['x', 'y'], 1, 0)).toEqual(['y', 'x']);
  });

  it('non modifica l\'array in ingresso', () => {
    const ingresso = [...ABCDE];
    const r = sposta(ingresso, 0, 3);
    expect(ingresso).toEqual(ABCDE);
    expect(r).not.toBe(ingresso);
  });

  it('con da === a restituisce comunque un array uguale (e non muta l\'ingresso)', () => {
    const ingresso = [...ABCDE];
    const r = sposta(ingresso, 2, 2);
    expect(r).toEqual(ABCDE);
    expect(ingresso).toEqual(ABCDE);
  });

  it('un indice di partenza fuori dalla lista non rompe e non cambia l\'ordine', () => {
    expect(sposta(ABCDE, 9, 1)).toEqual(ABCDE);
    expect(sposta(ABCDE, -1, 1)).toEqual(ABCDE);
  });
});

describe('spostaDiUno – frecce su/giù', () => {
  it('verso +1 (giù) porta l\'elemento al posto dopo', () => {
    expect(spostaDiUno(ABCDE, 1, 1)).toEqual(['a', 'c', 'b', 'd', 'e']);
  });

  it('verso -1 (su) porta l\'elemento al posto prima', () => {
    expect(spostaDiUno(ABCDE, 3, -1)).toEqual(['a', 'b', 'd', 'c', 'e']);
  });

  it('il primo non sale: nessun cambiamento', () => {
    expect(spostaDiUno(ABCDE, 0, -1)).toEqual(ABCDE);
  });

  it('l\'ultimo non scende: nessun cambiamento', () => {
    expect(spostaDiUno(ABCDE, 4, 1)).toEqual(ABCDE);
  });

  it('il primo può scendere e l\'ultimo può salire', () => {
    expect(spostaDiUno(ABCDE, 0, 1)).toEqual(['b', 'a', 'c', 'd', 'e']);
    expect(spostaDiUno(ABCDE, 4, -1)).toEqual(['a', 'b', 'c', 'e', 'd']);
  });

  it('un elemento solo, o lista vuota: nessun cambiamento', () => {
    expect(spostaDiUno(['x'], 0, 1)).toEqual(['x']);
    expect(spostaDiUno(['x'], 0, -1)).toEqual(['x']);
    expect(spostaDiUno([], 0, 1)).toEqual([]);
  });

  it('non modifica l\'array in ingresso, nemmeno ai bordi', () => {
    const ingresso = [...ABCDE];
    spostaDiUno(ingresso, 2, 1);
    spostaDiUno(ingresso, 0, -1);
    spostaDiUno(ingresso, 4, 1);
    expect(ingresso).toEqual(ABCDE);
  });

  it('è coerente con sposta di un posto', () => {
    expect(spostaDiUno(ABCDE, 2, 1)).toEqual(sposta(ABCDE, 2, 3));
    expect(spostaDiUno(ABCDE, 2, -1)).toEqual(sposta(ABCDE, 2, 1));
  });

  it('su e poi giù riporta all\'ordine di partenza', () => {
    expect(spostaDiUno(spostaDiUno(ABCDE, 3, -1), 2, 1)).toEqual(ABCDE);
  });
});
