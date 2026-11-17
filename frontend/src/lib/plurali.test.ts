import { describe, expect, it } from 'vitest';
import { crediti, daGiorni, giorni, quanti, turni } from './plurali';

describe('plurali', () => {
  it('uno al singolare, gli altri (e zero) al plurale', () => {
    expect(quanti(1, 'turno', 'turni')).toBe('1 turno');
    expect(quanti(0, 'turno', 'turni')).toBe('0 turni');
    expect(quanti(12, 'corso', 'corsi')).toBe('12 corsi');
    expect(quanti(1, 'offerta', 'offerte')).toBe('1 offerta');
  });
  it('le scorciatoie', () => {
    expect(crediti(1)).toBe('1 credito');
    expect(crediti('20')).toBe('20 crediti');
    expect(giorni(1)).toBe('1 giorno');
    expect(turni(2)).toBe('2 turni');
  });
});

describe('daGiorni', () => {
  it('oggi, ieri, poi i giorni', () => {
    expect(daGiorni(0)).toBe('da oggi');
    expect(daGiorni(1)).toBe('da ieri');
    expect(daGiorni(5)).toBe('da 5 giorni');
  });
});
