import { describe, expect, it } from 'vitest';
import { colonneGiorni } from './griglia';
import { dataLunga, oreTra } from './date';

describe('griglia', () => {
  it('le colonne dei giorni hanno etichetta, numero e oggi', () => {
    const c = colonneGiorni(['2026-10-19', '2026-10-20'], '2026-10-20');
    expect(c).toEqual([
      { chiave: '2026-10-19', etichetta: 'lun', numero: 19, oggi: false },
      { chiave: '2026-10-20', etichetta: 'mar', numero: 20, oggi: true },
    ]);
  });
});

describe('date', () => {
  it('ore tra due orari', () => {
    expect(oreTra('15:45:00', '19:15')).toBe(3.5);
    expect(oreTra('09:00', '13:15')).toBe(4.25);
  });
  it('data lunga', () => {
    expect(dataLunga('2026-01-01')).toBe('1 gennaio 2026');
  });
});
