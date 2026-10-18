import { describe, expect, it } from 'vitest';
import { COLONNE_SETTIMANA, colonneGiorni, daRiga } from './griglia';
import { dataLunga, oreTra } from './date';

describe('griglia', () => {
  it('le colonne dei giorni hanno etichetta, numero e oggi', () => {
    const c = colonneGiorni(['2026-10-19', '2026-10-20'], '2026-10-20');
    expect(c).toEqual([
      { chiave: '2026-10-19', etichetta: 'lun', numero: 19, oggi: false },
      { chiave: '2026-10-20', etichetta: 'mar', numero: 20, oggi: true },
    ]);
  });

  it('la settimana tipo va da lunedì (1) a domenica (7)', () => {
    expect(COLONNE_SETTIMANA.map((c) => c.chiave)).toEqual(['1', '2', '3', '4', '5', '6', '7']);
    expect(COLONNE_SETTIMANA[6].etichetta).toBe('domenica');
  });

  it('una riga del modello prende la mansione dalla postazione', () => {
    const v = daRiga({ id: 'r', postazioneId: 'p', postazione: 'Atrio', giornoSettimana: 3, inizio: '15:45:00', fine: '19:15:00', persona: null },
      'imp', new Map([['p', 'Assistente atrio']]));
    expect(v).toMatchObject({ colonna: '3', mansione: 'Assistente atrio', annullato: false, persona: null });
    expect(daRiga({ ...v, giornoSettimana: 1 } as never, 'imp', new Map()).mansione).toBe('Altro');
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
