import { describe, expect, it } from 'vitest';
import { BORDO, VERSIONI, misuraCornice, scala, versione } from './versioni';

describe('versioni della vetrina', () => {
  it('trova solo le versioni pronte', () => {
    expect(versione('gestionale-telefono')?.dispositivo).toBe('telefono');
    expect(versione('famiglie-telefono')).toBeUndefined();
    expect(versione('nessuna')).toBeUndefined();
    expect(versione(null)).toBeUndefined();
  });

  it('ha un id diverso per ogni versione', () => {
    expect(new Set(VERSIONI.map((v) => v.id)).size).toBe(VERSIONI.length);
  });

  it('ruota solo i dispositivi ruotabili', () => {
    const tablet = VERSIONI.find((v) => v.id === 'famiglie-tablet')!;
    const tel = VERSIONI.find((v) => v.id === 'gestionale-telefono')!;
    expect(misuraCornice(tablet, true)).toEqual({ larghezza: 1194, altezza: 834 });
    expect(misuraCornice(tablet, false)).toEqual({ larghezza: 834, altezza: 1194 });
    expect(misuraCornice(tel, true)).toEqual({ larghezza: 390, altezza: 844 });
    expect(misuraCornice(VERSIONI[0], false)).toBeUndefined();
  });

  it('rimpicciolisce la cornice solo quando non ci sta', () => {
    const tel = { larghezza: 390, altezza: 844 };
    expect(scala(tel, { larghezza: 1600, altezza: 1200 })).toBe(1);
    const s = scala(tel, { larghezza: 1400, altezza: 700 });
    expect(s).toBeCloseTo((700 - 48) / (844 + 2 * BORDO));
    expect(scala(tel, { larghezza: 0, altezza: 0 })).toBe(0.2);
  });
});
