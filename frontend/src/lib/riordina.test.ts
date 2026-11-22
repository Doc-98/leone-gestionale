import { describe, expect, it } from 'vitest';
import { annuncioSpostamento, postoDalPunto, sposta, spostaDiUno, stessoOrdine } from './riordina';

const ids = ['a', 'b', 'c', 'd'];

describe('sposta', () => {
  it('porta un elemento più in basso', () => expect(sposta(ids, 0, 2)).toEqual(['b', 'c', 'a', 'd']));
  it('porta un elemento più in alto', () => expect(sposta(ids, 3, 1)).toEqual(['a', 'd', 'b', 'c']));
  it('da uguale ad a: nessun cambio', () => expect(sposta(ids, 2, 2)).toEqual(ids));
  it('indici fuori elenco: nessun cambio', () => {
    expect(sposta(ids, -1, 2)).toEqual(ids);
    expect(sposta(ids, 1, 4)).toEqual(ids);
    expect(sposta(ids, 1.5, 2)).toEqual(ids);
  });
  it('non tocca l\'array ricevuto e ne restituisce una copia', () => {
    const originale = [...ids];
    const r = sposta(ids, 0, 3);
    expect(ids).toEqual(originale);
    expect(sposta(ids, 1, 1)).not.toBe(ids);
    expect(r).not.toBe(ids);
  });
  it('elenco vuoto o di uno', () => {
    expect(sposta([], 0, 0)).toEqual([]);
    expect(sposta(['x'], 0, 0)).toEqual(['x']);
  });
});

describe('spostaDiUno', () => {
  it('giù e su', () => {
    expect(spostaDiUno(ids, 1, 1)).toEqual(['a', 'c', 'b', 'd']);
    expect(spostaDiUno(ids, 2, -1)).toEqual(['a', 'c', 'b', 'd']);
  });
  it('ai bordi non cambia nulla', () => {
    expect(spostaDiUno(ids, 0, -1)).toEqual(ids);
    expect(spostaDiUno(ids, 3, 1)).toEqual(ids);
  });
  it('indice fuori elenco: nessun cambio', () => expect(spostaDiUno(ids, 9, -1)).toEqual(ids));
});

describe('annuncioSpostamento', () => {
  it('dice nome, posto e totale', () => expect(annuncioSpostamento('Satispay', 2, 5)).toBe('Satispay spostato al posto 2 di 5'));
});

describe('postoDalPunto', () => {
  const righe = [{ top: 0, bottom: 50 }, { top: 50, bottom: 100 }, { top: 100, bottom: 150 }];
  it('trova la riga sotto il puntatore', () => {
    expect(postoDalPunto(10, righe)).toBe(0);
    expect(postoDalPunto(60, righe)).toBe(1);
    expect(postoDalPunto(149, righe)).toBe(2);
  });
  it('sopra la prima è 0, sotto l\'ultima è l\'ultima', () => {
    expect(postoDalPunto(-30, righe)).toBe(0);
    expect(postoDalPunto(900, righe)).toBe(2);
  });
  it('senza righe è 0', () => expect(postoDalPunto(10, [])).toBe(0));
});

describe('stessoOrdine', () => {
  it('confronta posto per posto', () => {
    expect(stessoOrdine(['a', 'b'], ['a', 'b'])).toBe(true);
    expect(stessoOrdine(['a', 'b'], ['b', 'a'])).toBe(false);
    expect(stessoOrdine(['a'], ['a', 'b'])).toBe(false);
  });
});
