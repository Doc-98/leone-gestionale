import { describe, expect, it } from 'vitest';
import { corpoCheEntra } from './adattaCifra';

describe('corpoCheEntra', () => {
  it('se il testo ci sta, il corpo non cambia', () => {
    expect(corpoCheEntra(20, 80, 100, 11)).toBe(20);
    expect(corpoCheEntra(20, 100, 100, 11)).toBe(20);
  });
  it('se non ci sta, si rimpicciolisce in proporzione', () => {
    expect(corpoCheEntra(20, 200, 100, 11)).toBe(11);
    expect(corpoCheEntra(20, 125, 100, 11)).toBe(16);
  });
  it('mai sotto il minimo', () => {
    expect(corpoCheEntra(20, 1000, 100, 11)).toBe(11);
  });
  it('misure non valide: nessuna modifica', () => {
    expect(corpoCheEntra(20, 0, 100, 11)).toBe(20);
    expect(corpoCheEntra(20, 100, 0, 11)).toBe(20);
  });
});
