import { describe, expect, it } from 'vitest';
import { codiceValido, normalizzaCodice } from './nfc';

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
});
