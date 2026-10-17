import { describe, it, expect } from 'vitest';
import { aggiungiGiorni, lunedi, ora, giornoEsteso, formatOre } from './date';

describe('date', () => {
  it('somma giorni anche a cavallo del mese e dell’ora legale', () => {
    expect(aggiungiGiorni('2026-10-31', 1)).toBe('2026-11-01');
    expect(aggiungiGiorni('2026-10-24', 7)).toBe('2026-10-31');
  });
  it('lunedì della settimana', () => {
    expect(lunedi('2026-10-18')).toBe('2026-10-12'); // domenica
    expect(lunedi('2026-10-19')).toBe('2026-10-19');
  });
  it('formati', () => {
    expect(ora('15:00:00')).toBe('15:00');
    expect(giornoEsteso('2026-10-19')).toBe('lunedì 19 ottobre');
    expect(formatOre(4.25)).toBe('4,25');
    expect(formatOre(5)).toBe('5');
  });
});
