import { describe, expect, it } from 'vitest';
import { contaModifiche, interoFra, testoModifiche } from './salvataggio';

describe('contaModifiche', () => {
  it('uguali: zero', () => {
    expect(contaModifiche({ a: 1, b: 'x', c: [1, 2] }, { a: 1, b: 'x', c: [1, 2] })).toBe(0);
    expect(contaModifiche(null, undefined)).toBe(0);
    expect(contaModifiche('ciao', 'ciao')).toBe(0);
  });
  it('ogni campo diverso vale uno', () => {
    expect(contaModifiche({ a: 1, b: 'x' }, { a: 2, b: 'x' })).toBe(1);
    expect(contaModifiche({ a: 1, b: 'x' }, { a: 2, b: 'y' })).toBe(2);
  });
  it('entra negli oggetti e negli elenchi annidati', () => {
    const prima = { r: { attiva: true, scaglioni: [{ data: '2026-10-01', p: '20' }, { data: '2026-11-01', p: '10' }] } };
    const dopo = { r: { attiva: false, scaglioni: [{ data: '2026-10-01', p: '25' }, { data: '2026-11-01', p: '10' }] } };
    expect(contaModifiche(prima, dopo)).toBe(2);
  });
  it('un elemento aggiunto o tolto vale uno, qualunque cosa contenga', () => {
    expect(contaModifiche({ l: [{ a: 1, b: 2, c: 3 }] }, { l: [{ a: 1, b: 2, c: 3 }, { a: 4, b: 5, c: 6 }] })).toBe(1);
    expect(contaModifiche({ l: [1, 2, 3] }, { l: [1] })).toBe(2);
  });
  it('un campo che compare o sparisce conta, ma null e assente sono uguali', () => {
    expect(contaModifiche({ a: 1 }, { a: 1, b: 'x' })).toBe(1);
    expect(contaModifiche({ a: 1, b: null }, { a: 1 })).toBe(0);
    expect(contaModifiche({ a: null }, { a: 0 })).toBe(1);
  });
  it('tornare al valore di prima azzera il conto', () => {
    const o = { n: 'Piscina', v: true };
    expect(contaModifiche(o, { ...o, n: 'Piscina 2' })).toBe(1);
    expect(contaModifiche(o, { ...o, n: 'Piscina' })).toBe(0);
  });
});

describe('contaModifiche – numeri e testi', () => {
  it('un campo numerico riscritto come numero resta lo stesso valore', () => {
    expect(contaModifiche({ p: '10' }, { p: 10 })).toBe(0);
    expect(contaModifiche({ p: 10 }, { p: '10' })).toBe(0);
    expect(contaModifiche({ s: [{ p: '20' }] }, { s: [{ p: 25 }] })).toBe(1);
  });
  it('vuoto o diverso non è uguale', () => {
    expect(contaModifiche({ p: '10' }, { p: null })).toBe(1);
    expect(contaModifiche({ p: '' }, { p: 0 })).toBe(1);
    expect(contaModifiche({ p: '10' }, { p: 11 })).toBe(1);
  });
});

describe('contaModifiche – il vuoto', () => {
  it('stringa vuota, null e assente sono uguali', () => {
    expect(contaModifiche({ n: '' }, { n: null })).toBe(0);
    expect(contaModifiche({ n: null }, { n: '' })).toBe(0);
    expect(contaModifiche({ n: '' }, {})).toBe(0);
    expect(contaModifiche('', null)).toBe(0);
  });
  it('un campo numerico svuotato e tornato vuoto non è una modifica; scritto sì', () => {
    expect(contaModifiche({ cauzione: '' }, { cauzione: null })).toBe(0);
    expect(contaModifiche({ cauzione: '' }, { cauzione: 5 })).toBe(1);
    expect(contaModifiche({ cauzione: '5' }, { cauzione: null })).toBe(1);
  });
  it('lo zero non è vuoto', () => {
    expect(contaModifiche({ n: '' }, { n: 0 })).toBe(1);
    expect(contaModifiche({ n: null }, { n: 0 })).toBe(1);
  });
});

describe('testoModifiche', () => {
  it('singolare e plurale', () => {
    expect(testoModifiche(1)).toBe('1 modifica da salvare');
    expect(testoModifiche(2)).toBe('2 modifiche da salvare');
    expect(testoModifiche(12)).toBe('12 modifiche da salvare');
  });
});

describe('interoFra', () => {
  it('estremi compresi, come numero o come testo', () => {
    expect(interoFra(0, 0, 100)).toBe(true);
    expect(interoFra('100', 0, 100)).toBe(true);
    expect(interoFra(5, 1, 8)).toBe(true);
  });
  it('vuoto, fuori misura, decimale o non numero: no', () => {
    for (const v of [null, undefined, '', '  ', -1, 101, 150, 1.5, 'abc']) expect(interoFra(v, 0, 100)).toBe(false);
    expect(interoFra(0, 1, 8)).toBe(false);
  });
});
