import { describe, expect, it } from 'vitest';
import { euro, euroColonna, euroPerFile, importoNonValido, numero, numeroDa, perCampo } from './numeri';

const NB = '\u00a0';

describe('numeri', () => {
  it('raggruppa sempre le migliaia, anche sotto 10.000', () => {
    expect(numero(1175)).toBe('1.175');
    expect(numero(35)).toBe('35');
    expect(euroColonna(1175.5)).toBe(`1.175,50${NB}€`);
  });
  it('nel testo gli importi tondi non hanno decimali', () => {
    expect(euro(35)).toBe(`35${NB}€`);
    expect(euro(12.5)).toBe(`12,50${NB}€`);
    expect(euro(2140)).toBe(`2.140${NB}€`);
    expect(euro(null)).toBe('—');
    expect(euro(undefined)).toBe('—');
  });
  it('in colonna hanno sempre due decimali', () => {
    expect(euroColonna(35)).toBe(`35,00${NB}€`);
    expect(euroColonna(0.2)).toBe(`0,20${NB}€`);
    expect(euroColonna(-2)).toBe(`-2,00${NB}€`);
    expect(euroColonna(null)).toBe('—');
  });
  it('lo spazio prima di € non si spezza', () => {
    expect(euro(35)).not.toContain(' ');
    expect(euroColonna(35)).not.toContain(' ');
  });
  it('prepara un importo per un campo, con la virgola', () => {
    expect(perCampo(0.2)).toBe('0,20');
    expect(perCampo(9.5)).toBe('9,50');
    expect(perCampo(35)).toBe('35');
    expect(perCampo(1175.5)).toBe('1175,50');
    expect(perCampo(null)).toBe('');
  });
  it('legge un importo scritto con la virgola o col punto', () => {
    expect(numeroDa('0,2')).toBe(0.2);
    expect(numeroDa('0.2')).toBe(0.2);
    expect(numeroDa('1.234,50')).toBe(1234.5);
    expect(numeroDa('€ 20')).toBe(20);
    expect(numeroDa(`9,50${NB}€`)).toBe(9.5);
    expect(numeroDa('')).toBeNull();
    expect(numeroDa('abc')).toBeNull();
    expect(numeroDa('2.000')).toBe(2000);
    expect(numeroDa('1.175')).toBe(1175);
    expect(numeroDa('1.234.567')).toBe(1234567);
    expect(numeroDa('1,175.50')).toBe(1175.5);
    expect(numeroDa('1.175,5')).toBe(1175.5);
    expect(numeroDa('12,')).toBe(12);
    expect(numeroDa(' 3 ')).toBe(3);
    expect(numeroDa('-5')).toBe(-5);
    expect(numeroDa('0')).toBe(0);
    expect(numeroDa('12.5')).toBe(12.5);
    for (const x of ['1e3', '0x1A', '1,175', '1.1750', '1,2,3', '12.5.5', '1.2,3.4', ',', '.', '-', '5-', '1,17.5', '10 000,5x']) expect(numeroDa(x), x).toBeNull();
  });
  it('scrive e rilegge lo stesso importo', () => {
    for (const n of [0.2, 9.5, 35, 52.5, 0.05]) expect(numeroDa(perCampo(n))).toBe(n);
  });
  it('per i file niente punto delle migliaia', () => {
    expect(euroPerFile(1175.5)).toBe('1175,50');
  });
});

describe('importoNonValido', () => {
  it('vuoto non è sbagliato; testo e negativi sì', () => {
    expect(importoNonValido('')).toBe(false);
    expect(importoNonValido('  ')).toBe(false);
    expect(importoNonValido('35,50')).toBe(false);
    expect(importoNonValido('abc')).toBe(true);
    expect(importoNonValido('-5')).toBe(true);
    expect(importoNonValido('1,175')).toBe(true);
  });
});
