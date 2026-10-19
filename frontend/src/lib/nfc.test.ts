import { describe, expect, it } from 'vitest';
import { codiceDecimale, codiceValido, normalizzaCodice } from './nfc';

describe('codici dei braccialetti', () => {
  it('toglie i separatori e mette in maiuscolo', () => {
    expect(normalizzaCodice('04:a1:b2:c3')).toBe('04A1B2C3');
    expect(normalizzaCodice(' d000-0001 ')).toBe('D0000001');
  });
  it('accetta da 4 a 32 caratteri', () => {
    expect(codiceValido('D0000001')).toBe(true);
    expect(codiceValido('ABC')).toBe(false);
    expect(codiceValido('')).toBe(false);
  });
  it('converte il decimale a 10 cifre del gestionale attuale', () => {
    expect(normalizzaCodice('0209345710')).toBe('0C7A5CAE');
    expect(normalizzaCodice('0c:7a:5c:ae')).toBe('0C7A5CAE');
    expect(codiceDecimale('0C7A5CAE')).toBe('0209345710');
    expect(normalizzaCodice('12345678')).toBe('12345678'); // otto cifre: già esadecimale
  });
});
