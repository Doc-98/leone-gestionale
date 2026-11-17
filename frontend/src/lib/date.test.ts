import { describe, it, expect } from 'vitest';
import { aggiungiGiorni, dataAgenda, dataBreve, dataOra, dataOraFrase, giornoTitolo, lunedi, ora, giornoEsteso, formatOre, nomeMese, spostaMese, titoloSettimana, turnoSettimanale } from './date';

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
  it('mesi', () => {
    expect(nomeMese('2026-10')).toBe('ottobre 2026');
    expect(spostaMese('2026-12', 1)).toBe('2027-01');
    expect(spostaMese('2026-01', -1)).toBe('2025-12');
  });
  it('i formati dei giorni: agenda, registro, lungo, titolo', () => {
    expect(dataAgenda('2026-11-09')).toBe('lun 9/11');
    expect(dataAgenda('2026-12-06')).toBe('dom 6/12');
    expect(dataAgenda('2026-11-09T10:00:00Z')).toBe('lun 9/11');
    expect(dataBreve('2026-11-09')).toBe('09/11/2026');
    expect(giornoEsteso('2026-11-09')).toBe('lunedì 9 novembre');
    expect(giornoTitolo('2026-11-09')).toBe('Lunedì 9 novembre');
  });
  it('un turno che si ripete ogni settimana', () => {
    expect(turnoSettimanale(1, '17:00:00', '17:45:00')).toBe('lun 17:00–17:45');
    expect(turnoSettimanale(1, '17:00', '17:45', true)).toBe('lunedì 17:00–17:45');
    expect(turnoSettimanale(7, '09:30:00')).toBe('dom 09:30');
  });
  it('il titolo della settimana', () => {
    expect(titoloSettimana('2026-11-09')).toBe('9–15 novembre');
    expect(titoloSettimana('2026-10-26')).toBe('26 ottobre – 1 novembre');
    expect(titoloSettimana('2026-10-26', true)).toBe('26 ott – 1 nov');
    expect(titoloSettimana('2026-11-09', true)).toBe('9–15 novembre');
  });
  it('data e ora', () => {
    expect(dataOra('2026-10-12T15:04:00Z')).toBe('12/10/2026 · 17:04');
    expect(dataOraFrase('2026-10-12T15:04:00Z')).toBe('il 12/10/2026 alle 17:04');
  });
});
