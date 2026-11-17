import { describe, expect, it } from 'vitest';
import { etichettaVolte } from './listino';

describe('listino', () => {
  it('nomina le frequenze', () => {
    expect(etichettaVolte(null)).toBe('Prezzo unico');
    expect(etichettaVolte(0)).toBe('Open');
    expect(etichettaVolte(2)).toBe('Bi');
    expect(etichettaVolte(5)).toBe('5 volte');
  });
});
