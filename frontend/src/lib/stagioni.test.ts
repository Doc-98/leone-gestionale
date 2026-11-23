import { describe, expect, it } from 'vitest';
import { classePillStagione, ordinaStagioni, stagioneIniziale, statoStagione } from './stagioni';

const s = (id: string, dal: string, al: string) => ({ id, nome: id, dal, al });

describe('statoStagione', () => {
  const st = { dal: '2026-09-01', al: '2027-08-31' };
  it('gli estremi contano: primo e ultimo giorno sono in corso', () => {
    expect(statoStagione(st, '2026-09-01')).toBe('in corso');
    expect(statoStagione(st, '2027-08-31')).toBe('in corso');
    expect(statoStagione(st, '2027-01-15')).toBe('in corso');
  });
  it('prima è futura, dopo è finita', () => {
    expect(statoStagione(st, '2026-08-31')).toBe('futura');
    expect(statoStagione(st, '2027-09-01')).toBe('finita');
  });
});

describe('classePillStagione', () => {
  it('verde, grigia, azzurra', () => {
    expect(classePillStagione('in corso')).toBe('pill passa');
    expect(classePillStagione('finita')).toBe('pill');
    expect(classePillStagione('futura')).toBe('pill futura');
  });
});

describe('ordinaStagioni', () => {
  it('ordina per inizio senza toccare l\'array', () => {
    const a = [s('b', '2026-09-01', '2027-08-31'), s('a', '2025-09-01', '2026-08-31')];
    expect(ordinaStagioni(a).map((x) => x.id)).toEqual(['a', 'b']);
    expect(a.map((x) => x.id)).toEqual(['b', 'a']);
  });
});

describe('stagioneIniziale', () => {
  const finita = s('f', '2024-09-01', '2025-08-31');
  const corso = s('c', '2026-09-01', '2027-08-31');
  const futura1 = s('u1', '2027-09-01', '2028-08-31');
  const futura2 = s('u2', '2028-09-01', '2029-08-31');
  it('quella in corso', () => expect(stagioneIniziale([futura1, finita, corso], '2026-11-23')).toBe('c'));
  it('senza una in corso, la futura più vicina', () => expect(stagioneIniziale([futura2, futura1, finita], '2026-03-01')).toBe('u1'));
  it('con solo finite, l\'ultima', () => expect(stagioneIniziale([finita, s('g', '2025-09-01', '2026-08-31')], '2030-01-01')).toBe('g'));
  it('elenco vuoto: stringa vuota', () => expect(stagioneIniziale([], '2026-11-23')).toBe(''));
});
