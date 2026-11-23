import { describe, expect, it } from 'vitest';
import { cifreNonValide, giorniAnticipoNonValidi, parametroRegolaNonValido, percentualeNonValida, primaRataNonValida, scaglioneNonValido } from './validita';

describe('regole di validità', () => {
  it('giorni di anticipo: 0…365, interi', () => {
    for (const v of [0, 30, 365, '90']) expect(giorniAnticipoNonValidi(v)).toBe(false);
    for (const v of [-2, 366, 1.5, '', null, undefined]) expect(giorniAnticipoNonValidi(v)).toBe(true);
  });
  it('cifre del numero: 1…8', () => {
    for (const v of [1, 4, 8, '6']) expect(cifreNonValide(v)).toBe(false);
    for (const v of [-1, 0, 9, '', null]) expect(cifreNonValide(v)).toBe(true);
  });
  it('percentuale: 0…100', () => {
    for (const v of [0, 50, 100, '25']) expect(percentualeNonValida(v)).toBe(false);
    for (const v of [-5, 150, '', null]) expect(percentualeNonValida(v)).toBe(true);
  });
  it('uno scaglione senza data non conta; con la data serve la percentuale giusta', () => {
    expect(scaglioneNonValido({ data: '', percentuale: '' })).toBe(false);
    expect(scaglioneNonValido({ data: '2026-10-01', percentuale: '' })).toBe(true);
    expect(scaglioneNonValido({ data: '2026-10-01', percentuale: 150 })).toBe(true);
    expect(scaglioneNonValido({ data: '2026-10-01', percentuale: 20 })).toBe(false);
  });
  it('prima rata: 1…99', () => {
    for (const v of [1, 50, 99, '50']) expect(primaRataNonValida(v)).toBe(false);
    for (const v of [0, 100, -1, '', null]) expect(primaRataNonValida(v)).toBe(true);
  });
  it('parametro del tornello: 0…max; vuoto solo se facoltativo e spuntato', () => {
    expect(parametroRegolaNonValido(0, false, false, 240)).toBe(false);
    expect(parametroRegolaNonValido(240, false, false, 240)).toBe(false);
    expect(parametroRegolaNonValido(241, false, false, 240)).toBe(true);
    expect(parametroRegolaNonValido(-3, false, false, 90)).toBe(true);
    expect(parametroRegolaNonValido(null, false, false, 90)).toBe(true);
    expect(parametroRegolaNonValido('', false, true, 600)).toBe(true);
    expect(parametroRegolaNonValido(null, true, true, 600)).toBe(false);
    expect(parametroRegolaNonValido(null, true, false, 600)).toBe(true);
  });
});
